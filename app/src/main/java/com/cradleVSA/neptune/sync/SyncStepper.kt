package com.cradleVSA.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
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
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import com.fasterxml.jackson.core.json.ReaderBasedJsonParser
import com.fasterxml.jackson.core.json.UTF8StreamJsonParser
import com.fasterxml.jackson.databind.JsonNode
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * The idea of this class is to act as a stepper for the sync process and to let the calling object
 * know of the process know when its done the step through a call back than the caller will
 * call next step.
 *
 * TODO: Rewrite everything
 */
class SyncStepper(
    context: Context,
    private val stepperCallback: SyncStepperCallback
) {

    private val restApi: RestApi

    private val patientManager: PatientManager

    private val readingManager: ReadingManager

    private val sharedPreferences: SharedPreferences

    private val database: CradleDatabase

    init {
        // We have to do this since we're not getting SyncStepperImplementation injected (?)
        EntryPoints.get(context.applicationContext, SyncStepperInterface::class.java).let {
            restApi = it.getRestApi()
            patientManager = it.getPatientManager()
            readingManager = it.getReadingManager()
            sharedPreferences = it.getSharedPreferences()
            database = it.getDatabase()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncStepperInterface {
        fun getRestApi(): RestApi
        fun getPatientManager(): PatientManager
        fun getReadingManager(): ReadingManager
        fun getSharedPreferences(): SharedPreferences
        fun getDatabase(): CradleDatabase
    }

    /**
     * These variables keep track of all the upload and download requests made.
     */
    private lateinit var uploadRequestStatus: TotalRequestStatus
    private lateinit var downloadRequestStatus: TotalRequestStatus

    private val errorHashMap = HashMap<Int?, String?>()

    companion object {
        private const val TAG = "SyncStepper"
        private const val CHANNEL_BUFFER_CAPACITY = 250

        const val LAST_PATIENT_SYNC = "lastSyncTime"
        const val LAST_READING_SYNC = "lastSyncTimeReadings"
        const val LAST_SYNC_DEFAULT = 1L
    }

    private val syncMutex = Mutex()
    private var hasSynced = false

    suspend fun doSync(context: Context) = withContext(Default) {
        syncMutex.withLock {
            if (hasSynced) {
                return@withLock
            }
            doSyncInternal(context)
            hasSynced = true
        }
    }

    /**
     * Sync all patients first, then sync all the readings / referral / assessments
     */
    private suspend fun doSyncInternal(context: Context) {
        downloadRequestStatus = TotalRequestStatus(0, 0, 0)

        val lastPatientSyncTime = sharedPreferences.getLong(LAST_PATIENT_SYNC, LAST_SYNC_DEFAULT)
        val patientsToUpload: List<Patient> = patientManager.getPatientsForUpload()

        val patientResult: NetworkResult<Unit>
        val timeToSyncPatients = measureTimeMillis {
            patientResult = syncPatients(patientsToUpload, lastPatientSyncTime)
        }

        val patientsLeftToUpload = patientManager.getPatientsForUpload().size
        if (patientsLeftToUpload > 0) {
            // FIXME: Clean this up
            Log.wtf(
                TAG,
                "DEBUG: THERE ARE $patientsLeftToUpload patients" +
                    " LEFT TO UPLOAD"
            )
        }
        Log.d(TAG, "FINISH PATIENT SYNC. TIME TAKEN: $timeToSyncPatients")

        when (patientResult) {
            is Success -> {
                Log.d(TAG, "Success, moving on to syncing readings")
            }
            else -> {
                // don't sync readings if patient sync failed
                if (patientResult is Failure) {
                    errorHashMap[patientResult.statusCode] = patientResult.getErrorMessage(context)
                }

                withContext(Main) {
                    stepperCallback.onNewPatientAndReadingDownloadFinish(downloadRequestStatus)
                    finish(success = false)
                }
                return
            }
        }

        val lastReadingSyncTime = sharedPreferences.getLong(LAST_READING_SYNC, LAST_SYNC_DEFAULT)
        val readingsToUpload = readingManager.getUnUploadedReadings()

        val readingResult: NetworkResult<Unit>
        val timeToSyncReadings = measureTimeMillis {
            readingResult = syncReadings(readingsToUpload, lastReadingSyncTime)
        }

        val readingsLeftToUpload = readingManager.getUnUploadedReadings().size
        if (readingsLeftToUpload > 0) {
            Log.wtf(
                TAG,
                "THERE ARE $readingsLeftToUpload readings" +
                    " LEFT TO UPLOAD"
            )
            if (readingResult is Success) {
                // If it's successful, then all the readings must have been uploaded anyway
                val numMarked = readingManager.markAllReadingsAsUploaded()
                Log.wtf(
                    TAG,
                    "DEBUG: Readings now marked? " +
                        "${numMarked == readingsLeftToUpload}"
                )
            }
        }
        Log.d(TAG, "DEBUG FINISH READING SYNC. TIME TAKEN: $timeToSyncReadings")

        withContext(Main) {
            when (readingResult) {
                is Success -> {
                    stepperCallback.onNewPatientAndReadingDownloadFinish(
                        downloadRequestStatus
                    )
                    finish(success = true)
                }
                else -> {
                    if (readingResult is Failure) {
                        errorHashMap[readingResult.statusCode] = patientResult.getErrorMessage(context)
                    }

                    stepperCallback.onNewPatientAndReadingDownloadFinish(
                        downloadRequestStatus
                    )
                    finish(success = false)
                }
            }
        }
    }

    private suspend fun syncPatients(
        patientsToUpload: List<Patient>,
        lastSyncTime: Long
    ): NetworkResult<Unit> = withContext(Default) {
        Log.d(TAG, "preparing to upload ${patientsToUpload.size} patients")
        // TODO: Refactor this channel-download pattern

        return@withContext restApi.syncPatients(
            patientsToUpload,
            lastSyncTimestamp = lastSyncTime
        ) { inputStream ->
            var patientsDownloaded = 0
            val reader = JacksonMapper.readerForPatient
            try {
                withContext(Dispatchers.IO) {
                    val parser = reader.createParser(inputStream)
                    parser.parseObject {
                        when (currentName) {
                            PatientSyncField.TOTAL.text -> {
                                downloadRequestStatus.totalNum = nextIntValue(0)
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
                                    downloadRequestStatus.numUploaded = patientsDownloaded
                                    withContext(Main) {
                                        stepperCallback.onNewPatientAndReadingDownloading(
                                            downloadRequestStatus
                                        )
                                    }
                                }
                                if (buffer.isNotEmpty()) channel.send(buffer)
                                channel.close()
                                databaseJob.join()
                            }
                            PatientSyncField.FACILITIES.text -> {
                                nextToken()
                                val tree = readValueAsTree<JsonNode>().toPrettyString()
                                withContext(Main) {
                                    Log.d(
                                        TAG,
                                        "$parser: " +
                                            "ReaderBasedJsonParser? " +
                                            "${parser is ReaderBasedJsonParser}, " +
                                            "UTF8StreamJsonParser? " +
                                            "${parser is UTF8StreamJsonParser}, " +
                                            "The health facilities list is $tree"
                                    )
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
    ): NetworkResult<Unit> = withContext(Default) {
        Log.d(TAG, "preparing to upload ${readingsToUpload.size} readings")

        return@withContext restApi.syncReadings(
            readingsToUpload,
            lastSyncTimestamp = lastSyncTime
        ) { inputStream ->
            withContext(Main) {
                Log.d(TAG, "Parsing readings now")
            }
            var numReadingsDownloaded = 0
            var numReferralsDownloaded = 0
            var numAssessmentsDownloaded = 0

            val readerForReading = JacksonMapper.createReader<Reading>()
            val readerForReferral = JacksonMapper.createReader<Referral>()
            val readerForAssessment = JacksonMapper.createReader<Assessment>()
            try {
                val parser = readerForReading.createParser(inputStream)
                parser.parseObject {
                    when (currentName) {
                        ReadingSyncField.TOTAL.text -> {
                            val total = nextIntValue(0)
                            downloadRequestStatus.totalNum += total
                            withContext(Main) {
                                Log.d(TAG, "There are $total readings + referrals + followups")
                            }
                        }
                        ReadingSyncField.READINGS.text -> {
                            withContext(Main) {
                                Log.d(TAG, "Starting to parse readings array")
                            }

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

                                numReadingsDownloaded++
                                downloadRequestStatus.numUploaded++
                                withContext(Main) {
                                    stepperCallback.onNewPatientAndReadingDownloading(
                                        downloadRequestStatus
                                    )
                                }
                            }
                            if (buffer.isNotEmpty()) channel.send(buffer)
                            channel.close()
                            databaseJob.join()
                        }
                        ReadingSyncField.NEW_REFERRALS.text -> {
                            withContext(Main) {
                                Log.d(TAG, "Starting to parse NEW_REFERRALS array")
                            }

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
                                numReferralsDownloaded++
                                downloadRequestStatus.numUploaded++
                                withContext(Main) {
                                    stepperCallback.onNewPatientAndReadingDownloading(
                                        downloadRequestStatus
                                    )
                                }
                            }
                            channel.close()
                            databaseJob.join()
                        }
                        ReadingSyncField.NEW_FOLLOW_UPS.text -> {
                            withContext(Main) {
                                Log.d(TAG, "Starting to parse NEW_FOLLOW_UPS array")
                            }

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
                                numAssessmentsDownloaded++
                                downloadRequestStatus.numUploaded++
                                withContext(Main) {
                                    stepperCallback.onNewPatientAndReadingDownloading(
                                        downloadRequestStatus
                                    )
                                }
                            }
                            channel.close()
                            databaseJob.join()
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

    /**
     * saved last sync time in the shared pref. let the caller know
     */
    suspend fun finish(success: Boolean) = withContext(Default) {
        if (success) {
            val timestampToUse = UnixTimestamp.now
            sharedPreferences.edit(commit = true) {
                putLong(LAST_PATIENT_SYNC, timestampToUse)
                putLong(LAST_READING_SYNC, timestampToUse)
            }
        }
        withContext(Main) {
            stepperCallback.onFinish(errorHashMap)
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

/**
 * This class keeps track of total number of requests made, number of requests failed, and succeeded
 */
data class TotalRequestStatus(var totalNum: Int, var numFailed: Int, var numUploaded: Int) {

    fun allRequestCompleted(): Boolean {
        return (totalNum == numFailed + numUploaded)
    }

    fun allRequestsSuccess(): Boolean {
        return (totalNum == numUploaded)
    }
}
