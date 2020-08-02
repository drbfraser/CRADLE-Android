package com.cradle.neptune.view.sync

import android.content.Context
import android.content.SharedPreferences
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.JsonArray
import com.cradle.neptune.model.toList
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
import com.cradle.neptune.view.sync.ListUploader.UploadType.PATIENT
import com.cradle.neptune.view.sync.ListUploader.UploadType.READING
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The idea of this class is to act as a stepper for the sync process and to let the calling object
 * know of the process know when its done the step through a call back than the caller will
 * call next step.
 */
class SyncStepperClass(val context: Context, private val stepperCallback: SyncStepperCallback): SyncStepper{

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var updateApiData: UpdateApiData

    /**
     * These variables keep track of all the upload and download requests made.
     */
    private lateinit var uploadRequestStatus: TotalRequestStatus
    private lateinit var downloadRequestStatus:TotalRequestStatus

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }
    companion object {
        const val LAST_SYNC = "lastSyncTime"
        const val LAST_SYNC_DEFAULT = 0L
    }

    /**
     * step number 1, get all the newest data from the server. and go to step number 2.
     */
    override fun fetchUpdatesFromServer() {
        val lastSyncTime = sharedPreferences.getLong(LAST_SYNC, LAST_SYNC_DEFAULT)
        // give a timestamp to provide, again this will be from shared pref eventually
        volleyRequestManager.getUpdates(lastSyncTime) { result ->
            when (result) {
                is Success -> {
                    // result was success
                    updateApiData = UpdateApiData(result.value)
                    // let caller know of the next step
                    stepperCallback.onFetchDataCompleted(true)
                    GlobalScope.launch(Dispatchers.IO) {
                        // need to turn this into a patient json object that has readings inside of it
                        setupUploadingPatientReadings(lastSyncTime)
                    }
                }
                is Failure -> {
                    // let user know we failed.... probably cant continue?
                    stepperCallback.onFetchDataCompleted(false)
                }
            }
        }
    }

    /**
     * step 2 -> starts uploading readings and patients starting with patients.
     */
    override suspend fun setupUploadingPatientReadings(lastSyncTime: Long) {
        // get the brand new patients to upload
        val newPatients = ArrayList(patientManager.getUnUploadedPatients())
        val readingsToUpload = ArrayList(readingManager.getUnUploadedReadingsForServerPatients())
        // mock the time for now, in the future, this will be from shared pref
        val editedPatients = ArrayList(patientManager.getEditedPatients(lastSyncTime))

        // this will be edited patients that were not edited in the server, we match against the
        // downloaded patient id from the server to avoid conflicts
        with(editedPatients.iterator()) {
            forEach {
                if (updateApiData.editedPatientId.contains(it.id)) {
                    remove()
                }
            }
        }
        // in case local user made a new patient that is also in the server,
        // we want to avoid uploading the patient but upload the reading
        with(newPatients.iterator()){
            forEach {
                if (updateApiData.newPatientsIds.contains(it.patient.id)) {
                    readingsToUpload.addAll(it.readings)
                    remove()
                }
            }
        }

        // total number of uploads need to be done.
        val totalNum = newPatients.size + readingsToUpload.size + editedPatients.size
        // keep track of all the fail/pass status for uploaded requests
        uploadRequestStatus = TotalRequestStatus(totalNum, 0, 0)

        ListUploader(context, PATIENT, newPatients) { result ->
            // once finished call uploading a single patient, need to update the base time
            checkIfAllDataUploaded(result)
        }.startUpload()

        // these will be readings for existing patients that were not part
        ListUploader(context, READING, readingsToUpload) { result ->
            checkIfAllDataUploaded(result)
        }.startUpload()

        ListUploader(context, PATIENT, editedPatients) {
            checkIfAllDataUploaded(it)
        }.startUpload()

        // if we have nothing to upload, we start downloading right away
        if (totalNum == 0){
            stepperCallback.onNewPatientAndReadingUploadFinish(uploadRequestStatus)
            downloadAllInfo()
        }
    }

    /**
     * step 3 -> now we download all the information one by one
     */
    override fun downloadAllInfo() {
        val totalRequestNum = updateApiData.editedPatientId.size + updateApiData.newReadingsIds.size
        + updateApiData.followUpIds.size + updateApiData.newPatientsIds.size

         downloadRequestStatus = TotalRequestStatus(totalRequestNum, 0, 0)

        // download brand new patients that are not on the device
        updateApiData.newPatientsIds.toList().forEach {
            volleyRequestManager.getFullPatientById(it) { result ->
                // need to put them in the DB
                when (result) {
                    is Success -> {
                        val patient = result.value.first
                        patient.base = patient.lastEdited
                        val readings = result.value.second
                        readingManager.addAllReadings(readings);
                        patientManager.add(patient)
                        checkIfAllDataIsDownloaded(true)
                    }
                    is Failure -> {
                        checkIfAllDataIsDownloaded(false)
                    }
                }

            }
        }

        // download all the patients that we have but were edited by the server....
        // these include the patients we rejected to upload in step 2
        updateApiData.editedPatientId.toList().forEach {
            volleyRequestManager.getPatientOnlyInfo(it) { result->
                checkIfAllDataIsDownloaded(result)
            }
        }

        updateApiData.newReadingsIds.toList().forEach {
            // get all the readings that were created for existing patients from the server
            volleyRequestManager.getReadingById(it) {result ->
                checkIfAllDataIsDownloaded(result)
            }
        }

        updateApiData.followUpIds.toList().forEach {
            // make a volley request to get the requests
            volleyRequestManager.updateFollowUpById(it) { result ->
                checkIfAllDataIsDownloaded(result)
            }
        }
        // if there is nothing to download, call next step
        if (totalRequestNum == 0){
            stepperCallback.onNewPatientAndReadingDownloadFinish(downloadRequestStatus)
            finish()
        }
    }

    /**
     * saved last sync time in the shared pref. let the caller know
     */
    override fun finish(){
        sharedPreferences.edit().putLong(LAST_SYNC, System.currentTimeMillis()/1000L).apply()
        stepperCallback.onFinish()
    }

    /**
     * This function will check if we have gotten back all the results for all the upload network calls we made
     * [Synchronized] since multiple threads might be calling this function
     * @param result the latest result we got
     */
    @Synchronized
     private fun checkIfAllDataUploaded(result: Boolean) {
        if (result) {
            uploadRequestStatus.numUploaded++
        } else {
            uploadRequestStatus.numFailed++
        }
        // let the caller know we got another result
        stepperCallback.onNewPatientAndReadingUploading(uploadRequestStatus)

        if (uploadRequestStatus.allRequestCompleted()) {
            // call the next step
            stepperCallback.onNewPatientAndReadingUploadFinish(uploadRequestStatus)
            downloadAllInfo()
        }
    }

    /**
     * This function will check if we have gotten back all the results for all the download network calls we made
     * [Synchronized] since multiple threads might be calling this function
     * @param result the latest result we got
     */
    @Synchronized
    private fun checkIfAllDataIsDownloaded(result: Boolean) {
        if (result){
            downloadRequestStatus.numUploaded++
        } else {
            downloadRequestStatus.numFailed++
        }
        stepperCallback.onNewPatientAndReadingDownloading(downloadRequestStatus)
        if (downloadRequestStatus.allRequestCompleted()){
            stepperCallback.onNewPatientAndReadingDownloadFinish(downloadRequestStatus)
            finish()
        }
    }
}

/**
 * TODO name it better
 * contains information for the data we got through the update api
 */
data class UpdateApiData(val jsonArray: JSONObject) {
    val newPatientsIds =
        HashSet<String>(jsonArray.getJSONArray("newPatients").toList(JsonArray::getString))
    val editedPatientId =
        HashSet<String>(jsonArray.getJSONArray("editedPatients").toList(JsonArray::getString))

    // patients for these readings exists on the server so just download these readings...
    val newReadingsIds =
        HashSet<String>(jsonArray.getJSONArray("readings").toList(JsonArray::getString))

    // followup for referrals that were sent through the phone
    val followUpIds =
        HashSet<String>(jsonArray.getJSONArray("followups").toList(JsonArray::getString))
}

/**
 * This class keeps track of total number of requests made, number of requests failed, and succeded
 */
data class TotalRequestStatus(var totalNum: Int, var numFailed: Int, var numUploaded: Int) {

    fun allRequestCompleted(): Boolean {
        return (totalNum == numFailed + numUploaded)
    }

    fun allRequestsSuccess():Boolean{
        return (totalNum == numUploaded)
    }
}
