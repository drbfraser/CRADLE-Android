package com.cradle.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.SyncUpdate
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import org.threeten.bp.ZonedDateTime

/**
 * The idea of this class is to act as a stepper for the sync process and to let the calling object
 * know of the process know when its done the step through a call back than the caller will
 * call next step.
 */
@Suppress("LargeClass")
class SyncStepperImplementation(
    context: Context,
    private val stepperCallback: SyncStepperCallback
) : SyncStepper {

    private val restApi: RestApi

    private val patientManager: PatientManager

    private val readingManager: ReadingManager

    private val sharedPreferences: SharedPreferences

    private lateinit var updateApiData: SyncUpdate

    init {
        // We have to do this since we're not getting SyncStepperImplementation injected (?)
        EntryPoints.get(context.applicationContext, SyncStepperInterface::class.java).let {
            restApi = it.getRestApi()
            patientManager = it.getPatientManager()
            readingManager = it.getReadingManager()
            sharedPreferences = it.getSharedPreferences()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncStepperInterface {
        fun getRestApi(): RestApi
        fun getPatientManager(): PatientManager
        fun getReadingManager(): ReadingManager
        fun getSharedPreferences(): SharedPreferences
    }

    /**
     * These variables keep track of all the upload and download requests made.
     */
    private lateinit var uploadRequestStatus: TotalRequestStatus
    private lateinit var downloadRequestStatus: TotalRequestStatus

    private val errorHashMap = HashMap<Int?, String?>()

    companion object {
        const val LAST_SYNC = "lastSyncTime"
        const val LAST_SYNC_DEFAULT = 0L
    }

    /**
     * step number 1, get all the newest data from the server. and go to step number 2.
     */
    override suspend fun stepOneFetchUpdatesFromServer(context: Context) = withContext(Default) {
        val lastSyncTime = sharedPreferences.getLong(LAST_SYNC, LAST_SYNC_DEFAULT)
        // give a timestamp to provide, again this will be from shared pref eventually
        when (val result = restApi.getUpdates(lastSyncTime)) {
            is Success -> {
                // result was success
                updateApiData = result.value
                // let caller know of the next step
                withContext(Main) {
                    stepperCallback.onFetchDataCompleted(true)
                }
                Log.d("bugg", "current: ${Thread.currentThread().name}")
                stepTwoSetupUploadingPatientReadings(lastSyncTime, context)
            }
            is Failure -> {
                // let user know we failed.... probably cant continue?
                errorHashMap[result.statusCode] = result.getErrorMessage(context)
                withContext(Main) {
                    stepperCallback.onFetchDataCompleted(false)
                }
                finish(false)
            }
        }
    }

    /**
     * step 2 -> starts uploading readings and patients starting with patients.
     * Be careful changing code in this function.
     */
    override suspend fun stepTwoSetupUploadingPatientReadings(
        lastSyncTime: Long,
        context: Context
    ) = withContext(Default) {
        // get the brand new patients to upload
        val newPatientsToUpload: ArrayList<PatientAndReadings> =
            patientManager.getUnUploadedPatients() as ArrayList<PatientAndReadings>

        val readingsToUpload: ArrayList<Reading> =
            readingManager.getUnUploadedReadingsForServerPatients() as ArrayList<Reading>

        val editedPatientsToUpload: ArrayList<Patient> =
            patientManager.getEditedPatients(lastSyncTime) as ArrayList<Patient>

        // this will be edited patients that were not edited in the server, we match against the
        // downloaded patient id from the server to avoid conflicts
        with(editedPatientsToUpload.iterator()) {
            forEach {
                if (updateApiData.editedPatientsIds.contains(it.id)) {
                    remove()
                }
            }
        }
        // in case local user made a new patient that is also in the server,
        // we want to avoid uploading the patient but upload the reading
        with(newPatientsToUpload.iterator()) {
            forEach {
                if (updateApiData.newPatientsIds.contains(it.patient.id)) {
                    readingsToUpload.addAll(it.readings)
                    remove()
                }
            }
        }

        // catch the case where patient exists and a referral reading was sent. we now know reading
        // exists on the server.
        with(readingsToUpload.iterator()) {
            forEach {
                if (updateApiData.newReadingsIds.contains(it.id)) {
                    remove()
                }
            }
        }

        // total number of uploads need to be done.
        val totalNum =
            newPatientsToUpload.size + readingsToUpload.size + editedPatientsToUpload.size

        // keep track of all the fail/pass status for uploaded requests
        uploadRequestStatus = TotalRequestStatus(totalNum, 0, 0)

        newPatientsToUpload.forEach {
            val result = restApi.postPatient(it)
            when (result) {
                is Success -> {
                    it.patient.base = it.patient.lastEdited
                    patientManager.add(it.patient)
                    result.value.readings.forEach { reading ->
                        reading.isUploadedToServer = true
                    }
                    readingManager.addAllReadings(result.value.readings)
                }
            }
            checkIfAllDataUploaded(result, context)
        }

        readingsToUpload.forEach {
            val result = readingManager.uploadNewReadingToServer(it)
            checkIfAllDataUploaded(result, context)
        }

        editedPatientsToUpload.forEach {
            val result = patientManager.updatePatientOnServer(it)
            checkIfAllDataUploaded(result, context)
        }

        // if we have nothing to upload, we start downloading right away
        if (totalNum == 0) {
            withContext(Main) {
                stepperCallback.onNewPatientAndReadingUploadFinish(uploadRequestStatus)
            }
            stepThreeDownloadAllInfo(context)
        }
    }

    /**
     * step 3 -> now we download all the information one by one
     */
    override suspend fun stepThreeDownloadAllInfo(context: Context) = withContext(Default) {
        val totalRequestNum =
            updateApiData.editedPatientsIds.size + updateApiData.newReadingsIds.size +
                updateApiData.followupIds.size + updateApiData.newPatientsIds.size

        downloadRequestStatus = TotalRequestStatus(totalRequestNum, 0, 0)

        // download brand new patients that are not on the device
        updateApiData.newPatientsIds.toList().forEach {
            val result = patientManager.downloadPatientAndReading(it)
            when (result) {
                is Success -> {
                    patientManager.add(result.value.patient)
                    val readings = result.value.readings
                    readings.forEach { reading ->
                        reading.isUploadedToServer = true
                    }
                    readingManager.addAllReadings(readings)
                }
            }
            checkIfAllDataIsDownloaded(result, context)
        }

        // download all the patients that we have but were edited by the server....
        // these include the patients we rejected to upload in step 2
        updateApiData.editedPatientsIds.toList().forEach {
            val result = patientManager.downloadEditedPatientInfoFromServer(it)
            checkIfAllDataIsDownloaded(result, context)
        }

        updateApiData.newReadingsIds.toList().forEach {
            val result = readingManager.downloadNewReadingFromServer(it)
            checkIfAllDataIsDownloaded(result, context)
        }

        updateApiData.followupIds.toList().forEach {
            val result = readingManager.downloadAssessment(it)
            checkIfAllDataIsDownloaded(result, context)
        }
        // if there is nothing to download, call next step
        if (totalRequestNum == 0) {
            withContext(Main) {
                stepperCallback.onNewPatientAndReadingDownloadFinish(downloadRequestStatus)
            }
            finish(true)
        }
    }

    /**
     * saved last sync time in the shared pref. let the caller know
     */
    override suspend fun finish(success: Boolean) = withContext(Default) {
        if (success) {
            sharedPreferences.edit()
                .putLong(LAST_SYNC, ZonedDateTime.now().toEpochSecond()).apply()
        }
        withContext(Main) {
            stepperCallback.onFinish(errorHashMap)
        }
    }

    /**
     * This function will check if we have gotten back all the results for all the upload network calls we made
     * [Synchronized] since multiple threads might be calling this function
     * @param result the latest result we got
     */
    @Synchronized
    private suspend fun checkIfAllDataUploaded(result: NetworkResult<*>, context: Context) {
        when (result) {
            is Success -> {
                uploadRequestStatus.numUploaded++
            }
            is Failure -> {
                uploadRequestStatus.numFailed++
                errorHashMap[result.statusCode] = result.getErrorMessage(context)
            }
        }

        // let the caller know we got another result
        withContext(Main) {
            stepperCallback.onNewPatientAndReadingUploading(uploadRequestStatus)
        }

        if (uploadRequestStatus.allRequestCompleted()) {
            // call the next step
            withContext(Main) {
                stepperCallback.onNewPatientAndReadingUploadFinish(uploadRequestStatus)
            }
            stepThreeDownloadAllInfo(context)
        }
    }

    /**
     * This function will check if we have gotten back all the results for all the download network calls we made
     * [Synchronized] since multiple threads might be calling this function
     * @param result the latest result we got.
     */
    @Synchronized
    private suspend fun checkIfAllDataIsDownloaded(result: NetworkResult<*>, context: Context) {
        when (result) {
            is Success -> {
                downloadRequestStatus.numUploaded++
            }
            is Failure -> {
                downloadRequestStatus.numFailed++
                errorHashMap[result.statusCode] = result.getErrorMessage(context)
            }
        }
        withContext(Main) {
            stepperCallback.onNewPatientAndReadingDownloading(downloadRequestStatus)
        }
        if (downloadRequestStatus.allRequestCompleted()) {
            withContext(Main) {
                stepperCallback.onNewPatientAndReadingDownloadFinish(downloadRequestStatus)
            }
            finish(true)
        }
    }
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
