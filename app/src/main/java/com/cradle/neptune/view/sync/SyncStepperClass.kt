package com.cradle.neptune.view.sync

import android.content.Context
import android.util.Log
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
class SyncStepperClass(val context: Context, val stepperCallback: SyncStepperCallback) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var readingManager: ReadingManager

    private lateinit var downloadedData: DownloadedData

    private lateinit var uploadRequestStatus: TotalRequestStatus

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }

    /**
     * step number 1, get all the newest data from the server. and go to step number 2.
     */
    fun fetchUpdatesFromServer() {
        // give a timestamp to provide, again this will be from shared pref eventually
        val currTime = 1596091983L
        volleyRequestManager.getUpdates(currTime) { result ->
            when (result) {
                is Success -> {
                    // result was success
                    downloadedData = DownloadedData(result.value)
                    // let caller know of the next step
                    stepperCallback.onFetchDataCompleted(true)
                    GlobalScope.launch(Dispatchers.IO) {
                        // need to turn this into a patient json object that has readings inside of it
                        setupUploadingPatientReadings()
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
    private suspend fun setupUploadingPatientReadings() {
        // get the brand new patients to upload
        val newPatients = ArrayList(patientManager.getUnUploadedPatients())
        val readingsToUpload = ArrayList(readingManager.getUnUploadedReadingsForServerPatients())
        //mock the time for now, in the future, this will be from sharedpref
        val editedPatients = ArrayList(patientManager.getEditedPatients(1596091783L))
        Log.d("bugg","new patients to upload: "+ editedPatients.size)
        newPatients.forEach {
            Log.d("bugg","  "+ it.patient.id)
        }
        Log.d("bugg","new readings to upload: "+ readingsToUpload.size)
        readingsToUpload.forEach {
            Log.d("bugg"," "+ it.id)
        }
        // total number of uploads need to be done.
        val totalNum = newPatients.size + readingsToUpload.size+ editedPatients.size
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


        // this will be edited patients that were not edited in the server, we match againt the downloaded object
        editedPatients.forEach {
            if (downloadedData.editedPatientId.contains(it.id)){
                editedPatients.remove(it)
            }
        }
        Log.d("bugg","edited patients (should be empty) to upload: "+ editedPatients.forEach { it.id})
        ListUploader(context, PATIENT, editedPatients) {
            checkIfAllDataUploaded(it)
        }

    }

    /**
     * This function will check if we have gotten back all the results for the network calls we made
     * @param result the last result we got
     */
    private fun checkIfAllDataUploaded(
        result: Boolean
    ) {
        if (result) {
            uploadRequestStatus.numUploaded++
        } else {
            uploadRequestStatus.numFailed++
        }
        // let the caller know we got another result
        stepperCallback.onNewPatientAndReadingUploaded(uploadRequestStatus)

        if (uploadRequestStatus.allRequestCompleted()) {
            // call the next step
           // downloadAllInfo()
        }
    }

    /**
     * step 3 -> now we download all the information one by one
     */
    private fun downloadAllInfo() {
        val totalRequest = downloadedData.editedPatientId.size + downloadedData.newReadingsIds.size
        +downloadedData.followUpIds.size+ downloadedData.newPatientsIds.size

        val downloadStatus = TotalRequestStatus(totalRequest,0,0)
        // download brand new patients that are not on local
        downloadedData.newPatientsIds.toList().forEach {
            volleyRequestManager.getFullPatientById(it) {
                //need to put them in the DB
                Log.d("bugg","downloaded patient: "+ it.toString())
            }
        }
        // grab all the patients that we have but were edited by the server....
        // //these include the patients we rejected to upload
        downloadedData.editedPatientId.toList().forEach {
            volleyRequestManager.getPatientOnlyInfo(it) {
            }
        }

        downloadedData.newReadingsIds.toList().forEach {
            // get all the readings that were created for existing patients from the server
            volleyRequestManager.getReadingById(it) {
            }
        }

        downloadedData.followUpIds.toList().forEach {
            // make a volley request to get the requests
            volleyRequestManager.updateFollowUpById(it) {
            }
        }

        // once all these are downloaded, let activity know the process has completed.
    }
}

/**
 * A simple interface to let the activity know whats going on with the upload status
 */
interface SyncStepperCallback {

    /**
     * Let the caller know we have completed fetching the data from the server
     * @param success status of fetching the data from the server
     */
    fun onFetchDataCompleted(success:Boolean)

    /**
     * called everytime we get a network result for all the upload network calls
     * @param totalRequestStatus contains number of total requests, failed requests, success requests
     */
    fun onNewPatientAndReadingUploaded(totalRequestStatus: TotalRequestStatus)

    fun onStepThree()
    fun onStepFour()
}

/**
 * TODO name it better
 * contains information for the data we got through the update api
 */
data class DownloadedData(val jsonArray: JSONObject) {
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
}
