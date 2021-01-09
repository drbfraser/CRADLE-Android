package com.cradleVSA.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.ext.Field
import com.cradleVSA.neptune.ext.jackson.parseObject
import com.cradleVSA.neptune.ext.jackson.parseObjectArray
import com.cradleVSA.neptune.manager.PatientManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.Referral
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkException
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.utilitiles.RateLimitRunner
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A Worker that syncs the local [Patient]s and [Reading]s (and [Referral]s sent by SMS) with
 * the CRADLE server. The data that is on the phone but not on the server is first uploaded, then
 * new data from the server is downloaded. Syncing is done using a timestamp passed as a parameter
 * when accessing the API.
 */
class SyncWorker @WorkerInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val restApi: RestApi,
    private val patientManager: PatientManager,
    private val readingManager: ReadingManager,
    private val sharedPreferences: SharedPreferences,
    private val database: CradleDatabase
) : CoroutineWorker(context, params) {

    enum class State {
        STARTING,
        /** Checking the server for patients by uploading an empty list */
        CHECKING_SERVER_PATIENTS,
        UPLOADING_PATIENTS,
        DOWNLOADING_PATIENTS,
        /**
         * Checking the server for new readings, referrals, assessments by uploading an empty list
         */
        CHECKING_SERVER_READINGS,
        UPLOADING_READINGS,
        DOWNLOADING_READINGS
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val CHANNEL_BUFFER_CAPACITY = 250

        const val LAST_PATIENT_SYNC = "lastSyncTime"
        const val LAST_READING_SYNC = "lastSyncTimeReadings"
        const val LAST_SYNC_DEFAULT = 1L

        private const val PROGRESS_CURRENT_STATE = "currentState"
        private const val PROGRESS_TOTAL_NUMBER = "total"
        private const val PROGRESS_NUMBER_SO_FAR = "number_so_far"

        private const val RESULT_MESSAGE = "error_message"

        /**
         * Given a [WorkInfo] instance from WorkManager's getWorkInfo* methods for observing
         * intermediate progress, it gets the current syncing state.
         */
        fun getState(workInfo: WorkInfo) = State
            .valueOf(workInfo.progress.getString(PROGRESS_CURRENT_STATE) ?: State.STARTING.name)

        /**
         * Get the progress for the current state in the [workInfo]'s progress.
         *
         * e.g., for downloading, it will be the number of patients or readings downloaded out of
         * the total number that are downloaded
         */
        fun getProgress(workInfo: WorkInfo): Pair<Int, Int>? {
            val progress = workInfo.progress.getInt(PROGRESS_NUMBER_SO_FAR, -1)
            val total = workInfo.progress.getInt(PROGRESS_TOTAL_NUMBER, -1)
            @Suppress("ComplexCondition")
            return if (
                (progress == -1 && total == -1) ||
                // Don't bother showing progress if there's nothing to download.
                (progress == 0 && total == 0)
            ) {
                null
            } else {
                progress to total
            }
        }

        /**
         * Given a *finished* [WorkInfo] instance from WorkManager's getWorkInfo* method, it gets
         * the message of the sync result.
         */
        fun getSyncResultMessage(workInfo: WorkInfo): String? = workInfo.outputData.getString(
            RESULT_MESSAGE
        )
    }

    /**
     * To rate limit download updates.
     */
    private val rateLimitRunner = RateLimitRunner(seconds = 0.05)

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork()")
        setProgress(workDataOf(PROGRESS_CURRENT_STATE to State.STARTING.name))

        val lastPatientSyncTime = sharedPreferences.getLong(
            LAST_PATIENT_SYNC,
            LAST_SYNC_DEFAULT
        )
        // We only use the timestamp right before the internet call is made.
        // We do not use the timestamp after syncing is done; there could be a case where someone
        // edits a patient or adds a reading etc. during the sync; those changes will never the
        // phone if we only use a timestamp after the sync.
        val syncTimestampToSave: Long = UnixTimestamp.now

        val patientsToUpload: List<Patient> = patientManager.getPatientsForUpload()
        val patientResult = syncPatients(patientsToUpload, lastPatientSyncTime)
        val patientsLeftToUpload = patientManager.getPatientsForUpload().size
        if (patientsLeftToUpload > 0) {
            // FIXME: Clean this up
            Log.wtf(
                TAG,
                "DEBUG: THERE ARE $patientsLeftToUpload patients" +
                    " LEFT TO UPLOAD"
            )
        }

        if (patientResult is Success) {
            sharedPreferences.edit(commit = true) {
                putLong(LAST_PATIENT_SYNC, syncTimestampToSave)
            }
            Log.d(TAG, "Patient sync is a success, moving on to syncing readings")
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultMessage(patientResult))
            )
        }

        val lastReadingSyncTime = sharedPreferences.getLong(
            LAST_READING_SYNC,
            LAST_SYNC_DEFAULT
        )
        val readingsToUpload = readingManager.getUnUploadedReadings()
        val readingResult = syncReadings(readingsToUpload, lastReadingSyncTime)
        val readingsLeftToUpload = readingManager.getUnUploadedReadings().size
        if (readingsLeftToUpload > 0) {
            Log.wtf(
                TAG,
                "There are $readingsLeftToUpload readings left to upload"
            )
            if (readingResult is Success) {
                // If it's successful, then all the readings must have been uploaded anyway
                val numMarked = readingManager.markAllReadingsAsUploaded()
                Log.d(
                    TAG,
                    "DEBUG: Readings now marked? " +
                        "${numMarked == readingsLeftToUpload}"
                )
            }
        }

        return if (readingResult is Success) {
            sharedPreferences.edit(commit = true) {
                putLong(LAST_READING_SYNC, syncTimestampToSave)
            }
            Result.success(
                workDataOf(RESULT_MESSAGE to getResultMessage(readingResult))
            )
        } else {
            Result.failure(workDataOf(RESULT_MESSAGE to getResultMessage(readingResult)))
        }
    }

    private suspend fun syncPatients(
        patientsToUpload: List<Patient>,
        lastSyncTime: Long
    ): NetworkResult<Unit> = withContext(Dispatchers.Default) {
        setProgress(
            if (patientsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_PATIENTS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_PATIENTS.name)
            }
        )
        Log.d(TAG, "preparing to upload ${patientsToUpload.size} patients")
        // TODO: Refactor this channel-download pattern

        return@withContext restApi.syncPatients(
            patientsToUpload,
            lastSyncTimestamp = lastSyncTime
        ) { inputStream ->
            var patientsDownloaded = 0
            var totalPatients = 0
            val reader = JacksonMapper.readerForPatient
            try {
                withContext(Dispatchers.IO) {
                    reader.createParser(inputStream).use { parser ->
                        parser.parseObject {
                            when (currentName) {
                                PatientSyncField.TOTAL.text -> {
                                    totalPatients = nextIntValue(0)
                                    if (totalPatients == 0) {
                                        return@use
                                    }
                                }
                                PatientSyncField.PATIENTS.text -> {
                                    val channel = Channel<List<Patient>>()
                                    val databaseJob = launch {
                                        database.withTransaction {
                                            for (patientList in channel) {
                                                for (patient in patientList) {
                                                    patientManager.add(patient)
                                                }
                                            }
                                            Log.d(TAG, "patient  database job is done")
                                        }
                                    }

                                    var buffer = ArrayList<Patient>(CHANNEL_BUFFER_CAPACITY)
                                    parseObjectArray<Patient>(reader) {
                                        buffer.add(it)
                                        if (buffer.size >= CHANNEL_BUFFER_CAPACITY - 1) {
                                            channel.send(buffer)
                                            buffer = ArrayList(CHANNEL_BUFFER_CAPACITY)
                                        }
                                        patientsDownloaded++
                                        reportProgress(
                                            state = State.DOWNLOADING_PATIENTS,
                                            progress = patientsDownloaded,
                                            total = totalPatients,
                                        )
                                    }
                                    reportProgress(
                                        state = State.DOWNLOADING_PATIENTS,
                                        progress = patientsDownloaded,
                                        total = totalPatients,
                                        bypassRateLimit = true
                                    )
                                    if (buffer.isNotEmpty()) channel.send(buffer)
                                    channel.close()
                                    databaseJob.join()
                                }
                                PatientSyncField.FACILITIES.text -> {
                                    // TODO: Either have a sync endpoint for new facilities, or
                                    //  remove this and just redownload facilities from server.
                                    // nextToken()
                                    // val tree = readValueAsTree<JsonNode>().toPrettyString()
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "exception while reading patients", e)
                // Propagate exceptions to the Http class so that it can log it
                throw e
            }
            Log.d(TAG, "DEBUG: Downloaded $patientsDownloaded patients.")
        }
    }

    private suspend fun syncReadings(
        readingsToUpload: List<Reading>,
        lastSyncTime: Long
    ): NetworkResult<Unit> = withContext(Dispatchers.Default) {
        Log.d(TAG, "preparing to upload ${readingsToUpload.size} readings")
        setProgress(
            if (readingsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_READINGS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_READINGS.name)
            }
        )
        return@withContext restApi.syncReadings(
            readingsToUpload,
            lastSyncTimestamp = lastSyncTime
        ) { inputStream ->
            Log.d(TAG, "Parsing readings now")
            var numReadingsDownloaded = 0
            var numReferralsDownloaded = 0
            var numAssessmentsDownloaded = 0

            val readerForReading = JacksonMapper.createReader<Reading>()
            val readerForReferral = JacksonMapper.createReader<Referral>()
            val readerForAssessment = JacksonMapper.createReader<Assessment>()
            try {
                readerForReading.createParser(inputStream).use { parser ->
                    var totalDownloaded = 0
                    var total = 0
                    parser.parseObject {
                        when (currentName) {
                            ReadingSyncField.TOTAL.text -> {
                                total = nextIntValue(0)
                                Log.d(TAG, "There are $total readings + referrals + followups")

                                if (total == 0) {
                                    return@use
                                }
                            }
                            ReadingSyncField.READINGS.text -> {
                                Log.d(TAG, "Starting to parse readings array")

                                val channel = Channel<List<Reading>>()
                                val databaseJob = launch {
                                    database.withTransaction {
                                        for (readingList in channel) {
                                            for (reading in readingList) {
                                                readingManager.addReading(
                                                    reading,
                                                    isReadingFromServer = true
                                                )
                                            }
                                        }
                                    }
                                }

                                // Channels have some non-significant overhead, so we use a local
                                // buffer.
                                // TODO: Refactor this channel download stuff
                                var buffer = ArrayList<Reading>(CHANNEL_BUFFER_CAPACITY)
                                parseObjectArray<Reading>(readerForReading) {
                                    buffer.add(it)
                                    if (buffer.size >= CHANNEL_BUFFER_CAPACITY - 1) {
                                        channel.send(buffer)
                                        buffer = ArrayList(CHANNEL_BUFFER_CAPACITY)
                                    }

                                    totalDownloaded++
                                    numReadingsDownloaded++
                                    reportProgress(
                                        State.DOWNLOADING_READINGS,
                                        progress = totalDownloaded,
                                        total = total,
                                    )
                                }
                                if (buffer.isNotEmpty()) channel.send(buffer)
                                channel.close()
                                databaseJob.join()
                            }
                            ReadingSyncField.NEW_REFERRALS.text -> {
                                Log.d(TAG, "Starting to parse NEW_REFERRALS array")

                                // TODO: refactor
                                val channel = Channel<Referral>()
                                val databaseJob = launch {
                                    database.withTransaction {
                                        for (referral in channel) {
                                            readingManager.addReferral(referral)
                                        }
                                    }
                                }
                                parseObjectArray<Referral>(readerForReferral) {
                                    channel.send(it)
                                    totalDownloaded++
                                    numReferralsDownloaded++
                                    reportProgress(
                                        State.DOWNLOADING_READINGS,
                                        progress = totalDownloaded,
                                        total = total,
                                    )
                                }
                                channel.close()
                                databaseJob.join()
                            }
                            ReadingSyncField.NEW_FOLLOW_UPS.text -> {
                                Log.d(TAG, "Starting to parse NEW_FOLLOW_UPS array")

                                // TODO: refactor
                                val channel = Channel<Assessment>()
                                val databaseJob = launch {
                                    database.withTransaction {
                                        for (assessment in channel) {
                                            readingManager.addAssessment(assessment)
                                        }
                                    }
                                }
                                parseObjectArray<Assessment>(readerForAssessment) {
                                    channel.send(it)
                                    totalDownloaded++
                                    numAssessmentsDownloaded++
                                    reportProgress(
                                        State.DOWNLOADING_READINGS,
                                        progress = totalDownloaded,
                                        total = total,
                                    )
                                }
                                reportProgress(
                                    State.DOWNLOADING_READINGS,
                                    progress = totalDownloaded,
                                    total = total,
                                    bypassRateLimit = true
                                )
                                channel.close()
                                databaseJob.join()
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "exception while reading readings / referrals / assessments", e)
                // Propagate exceptions to the Http class so that it can log it
                throw e
            }
            Log.d(
                TAG,
                "DEBUG: Downloaded $numReadingsDownloaded readings, " +
                    "$numReferralsDownloaded referrals and " +
                    "$numAssessmentsDownloaded assessments."
            )
        }
    }

    private suspend fun reportProgress(
        state: State,
        progress: Int,
        total: Int,
        bypassRateLimit: Boolean = false
    ) {
        if (bypassRateLimit) {
            setProgress(
                workDataOf(
                    PROGRESS_CURRENT_STATE to state.name,
                    PROGRESS_NUMBER_SO_FAR to progress,
                    PROGRESS_TOTAL_NUMBER to total
                )
            )
        } else {
            rateLimitRunner.runSuspend {
                setProgress(
                    workDataOf(
                        PROGRESS_CURRENT_STATE to state.name,
                        PROGRESS_NUMBER_SO_FAR to progress,
                        PROGRESS_TOTAL_NUMBER to total
                    )
                )
            }
        }
    }

    private fun getResultMessage(networkResult: NetworkResult<Unit>): String {
        return when (networkResult) {
            is Failure -> applicationContext.getString(
                R.string.sync_worker_failure_server_sent_error_code_d__s,
                networkResult.statusCode,
                networkResult.getErrorMessage(applicationContext)
            )
            is NetworkException -> applicationContext.getString(
                R.string.sync_worker_failure_exception_during_sync_s__s,
                networkResult.cause::class.java.simpleName,
                networkResult.cause.message
            )
            is Success -> applicationContext.getString(R.string.sync_worker_success)
        }
    }
}

private enum class PatientSyncField(override val text: String) : Field {
    TOTAL("total"),
    PATIENTS("patients"),
    FACILITIES("healthFacilities"),
}

private enum class ReadingSyncField(override val text: String) : Field {
    TOTAL("total"),
    READINGS("readings"),
    NEW_REFERRALS("newReferralsForOldReadings"),
    NEW_FOLLOW_UPS("newFollowupsForOldReadings")
}
