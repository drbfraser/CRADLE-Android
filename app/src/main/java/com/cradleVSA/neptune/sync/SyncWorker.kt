package com.cradleVSA.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.ext.Field
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
import com.cradleVSA.neptune.net.SyncException
import com.cradleVSA.neptune.utilitiles.RateLimitRunner
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * A Worker that syncs the local [Patient]s and [Reading]s (and [Referral]s sent by SMS) with
 * the CRADLE server. The data that is on the phone but not on the server is first uploaded, then
 * new data from the server is downloaded. Syncing is done using a timestamp passed as a parameter
 * when accessing the API.
 *
 * TODO: Make sure that patients or readings can't be edited or created while syncing is in
 *  progress.
 * TODO: Use SyncWorker to perform periodic sync.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
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

        /** SharedPreferences key for last time patients were synced */
        const val LAST_PATIENT_SYNC = "lastSyncTime"
        /** SharedPreferences key for last time readings were synced */
        const val LAST_READING_SYNC = "lastSyncTimeReadings"
        /** Default last sync timestamp. Note that using 0 will result in server rejecting param */
        const val LAST_SYNC_DEFAULT = "1"

        /** The key for current syncing state in the [WorkInfo] progress */
        private const val PROGRESS_CURRENT_STATE = "currentState"
        /** The key for total number to download in the [WorkInfo] progress */
        private const val PROGRESS_TOTAL_NUMBER = "total"
        /** The key for number to downloaded so far in the [WorkInfo] progress */
        private const val PROGRESS_NUMBER_SO_FAR = "number_so_far"
        /** The key for result of the syncing stored in the finished[WorkInfo] */
        private const val RESULT_MESSAGE = "result_message"

        /**
         * Given a [WorkInfo] instance from WorkManager's getWorkInfo* methods for observing
         * intermediate progress, it gets the current syncing state.
         */
        fun getState(workInfo: WorkInfo) = workInfo
            .progress
            .getString(PROGRESS_CURRENT_STATE)?.let { State.valueOf(it) } ?: State.STARTING

        /**
         * Get the progress for the current state in the [workInfo]'s progress.
         *
         * The returned [Pair] contains the progress for the current state (e.g., for downloading,
         * it will be the number of patients or readings downloaded out of the total number that are
         * downloaded). The [Pair] will be null if and only if there is no progress with the current
         * state (e.g., there might be nothing to download).
         *
         * For now, there is no uploading progress; only downloading has progress associated with
         * it.
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
     * This is needed in order to make the progress reporting work, because the parsing is way
     * too fast for the updates.
     */
    private val rateLimitRunner = RateLimitRunner(seconds = 0.075)

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork()")
        setProgress(workDataOf(PROGRESS_CURRENT_STATE to State.STARTING.name))

        val lastPatientSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_PATIENT_SYNC,
                LAST_SYNC_DEFAULT.toString()
            )!!
        )
        // We only use the timestamp right before the internet call is made.
        // We do not use the timestamp after syncing is done; there could be a case where someone
        // edits a patient or adds a reading etc. during the sync; those changes will never the
        // phone if we only use a timestamp after the sync.
        val syncTimestampToSave: BigInteger = UnixTimestamp.now

        val patientsToUpload: List<Patient> = patientManager.getPatientsForUpload()
        val patientResult = syncPatients(patientsToUpload, lastPatientSyncTime)
        val patientsLeftToUpload = patientManager.getPatientsForUpload().size
        if (patientsLeftToUpload > 0) {
            // FIXME: Clean this up
            Log.wtf(
                TAG,
                "DEBUG: THERE ARE $patientsLeftToUpload PATIENTS LEFT TO UPLOAD"
            )
        }

        if (patientResult is Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_PATIENT_SYNC, syncTimestampToSave.toString())
            }
            Log.d(TAG, "Patient sync is a success, moving on to syncing readings")
        } else {
            return Result.failure(
                workDataOf(RESULT_MESSAGE to getResultMessage(patientResult))
            )
        }

        val lastReadingSyncTime = BigInteger(
            sharedPreferences.getString(
                LAST_READING_SYNC,
                LAST_SYNC_DEFAULT
            )!!
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
                Log.wtf(TAG, "successful reading sync but still readings left unsynced")
                // https://csil-git1.cs.surrey.sfu.ca/415-cradle/cradle-platform/-/blob/master/server/api/resources/sync.py#L97-103
                //  The only reasons why a reading might still not be uploaded but the response from
                //  server is still successful is that:
                //  The server will skip readings that are for non-existent patients.
                //  although we should note this usually never happens, because it requires
                //  the Android client to somehow upload a because patients on Android are
                //  synced first before reading sync. Also, any patients that are sent
                //  through SMS are still treated as unuploaded in case the SMS did not
                //  actually reach the server.
            }
        }

        return if (readingResult is Success) {
            sharedPreferences.edit(commit = true) {
                putString(LAST_READING_SYNC, syncTimestampToSave.toString())
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
        lastSyncTime: BigInteger
    ): NetworkResult<Unit> = withContext(Dispatchers.Default) {
        setProgress(
            if (patientsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_PATIENTS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_PATIENTS.name)
            }
        )
        Log.d(TAG, "preparing to upload ${patientsToUpload.size} patients")
        val channel = Channel<Patient>()
        launch {
            try {
                database.withTransaction {
                    for (patient in channel) {
                        patientManager.add(patient)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "patients sync failed", e)
                }
            }
            withContext(Dispatchers.Main) { Log.d(TAG, "patients job done") }
        }

        restApi.syncPatients(
            patientsToUpload,
            lastSyncTimestamp = lastSyncTime,
            patientChannel = channel
        ) { current, total ->
            reportProgress(
                state = State.DOWNLOADING_PATIENTS,
                progress = current,
                total = total,
            )
        }
    }

    private suspend fun syncReadings(
        readingsToUpload: List<Reading>,
        lastSyncTime: BigInteger
    ): NetworkResult<Unit> = withContext(Dispatchers.Default) {
        Log.d(TAG, "preparing to upload ${readingsToUpload.size} readings")
        setProgress(
            if (readingsToUpload.isEmpty()) {
                workDataOf(PROGRESS_CURRENT_STATE to State.CHECKING_SERVER_READINGS.name)
            } else {
                workDataOf(PROGRESS_CURRENT_STATE to State.UPLOADING_READINGS.name)
            }
        )

        val readingChannel = Channel<Reading>()
        val referralChannel = Channel<Referral>()
        val assessmentChannel = Channel<Assessment>()

        launch {
            try {
                database.withTransaction {
                    for (reading in readingChannel) {
                        readingManager.addReading(reading, isReadingFromServer = true)
                    }
                    for (referral in referralChannel) {
                        readingManager.addReferral(referral)
                    }
                    for (assessment in assessmentChannel) {
                        readingManager.addAssessment(assessment)
                    }
                }
            } catch (e: SyncException) {
                // Need to switch context, since Dispatchers.Default doesn't do logging
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "reading sync failed", e)
                }
            }
        }

        restApi.syncReadings(
            readingsToUpload,
            lastSyncTimestamp = lastSyncTime,
            readingChannel,
            referralChannel,
            assessmentChannel
        ) { current, total ->
            reportProgress(
                State.DOWNLOADING_READINGS,
                progress = current,
                total = total,
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

enum class PatientSyncField(override val text: String) : Field {
    TOTAL("total"),
    PATIENTS("patients"),
    FACILITIES("healthFacilities"),
}

enum class ReadingSyncField(override val text: String) : Field {
    TOTAL("total"),
    READINGS("readings"),
    NEW_REFERRALS("newReferralsForOldReadings"),
    NEW_FOLLOW_UPS("newFollowupsForOldReadings")
}
