package com.cradle.neptune.view.sync

import android.content.Context
import android.util.Log
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.JsonArray
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
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

    lateinit var downloadedData: DownloadedData

    lateinit var uploadRequestStatus: UploadRequestStatus

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }

    /**
     * step number 1, get all the newest data from the server. and go to step number 2.
     */
    fun fetchUpdatesFromServer() {
        // give a timestamp to provide
        val currTime = 1596091983L
        volleyRequestManager.getUpdates(currTime) { result ->
            when (result) {
                is Success -> {
                    // result was success
                    downloadedData = DownloadedData(result.value)
                    // let caller know of the next step
                    stepperCallback.onFetchDataCompleted(downloadedData)
                    GlobalScope.launch(Dispatchers.IO) {
                        val patientsToUpload = getPatientToUpload()
                        val readingsToUpload = ArrayList(readingManager.getUnUploadedReadings())
                        setupUploadingPatientReadings(patientsToUpload, readingsToUpload)
                    }
                }
                is Failure -> {
                    // let user know we failed.... probably cant continue?
                }
            }
        }
    }

    private suspend fun getPatientToUpload(): ArrayList<Patient> {

        val unUploadedPatients = ArrayList(patientManager.getAllPatients())

        unUploadedPatients.forEach {
            if (downloadedData.editedPatientId.contains(it.id) || downloadedData.newPatientsIds.contains(
                    it.id
                )
            ) {
                unUploadedPatients.remove(it)
            }
        }
        return unUploadedPatients
    }

    /**
     * step 2 -> starts uploading readings and patients starting with patients.
     */
    private fun setupUploadingPatientReadings(
        patientsList: ArrayList<Patient>,
        readingList: ArrayList<Reading>
    ) {
        val totalNum = patientsList.size + readingList.size
        uploadRequestStatus = UploadRequestStatus(totalNum, 0, 0)
        // this will be all the new patients with their readings.
        ListUploader(context, PATIENT, patientsList) { result ->
            // once finished call uploading a single patient, need to update the base time
            // going to put patient inside readings later on
            Log.d("bugg", "starting to upload the readings")
            checkIfAllDataUploaded(result)
        }
        // how do  i get the readings for existing patients??
        // these will be readings for existing patients that were not part
        ListUploader(context, READING, readingList) { result ->
            // finished uploading the readings and show the overall status.
            checkIfAllDataUploaded(result)
        }

        // this will be edited patients that were not edited in the server, we match againt the downloaded object
        ListUploader(context, PATIENT, patientsList) {
            checkIfAllDataUploaded(it)
        }

        // todo once all the callbacks are done let server know and start next step.
    }

    /**
     * This function will check if we have gotten back all the results for the network calls we made
     */
    private fun checkIfAllDataUploaded(
        patientResult: Boolean
    ) {
        if (patientResult) {
            uploadRequestStatus.numUploaded++
        } else {
            uploadRequestStatus.numFailed++
        }
        stepperCallback.onNewPatientAndReadingUploaded(uploadRequestStatus)

        if (uploadRequestStatus.allRequestCompleted()) {
            // finished
            // call the next step
            downloadAllInfo()
        }
    }

    /**
     * step 3 -> now we download all the information one by one
     */
    private fun downloadAllInfo() {
        // download brand new patients that are not on local
        downloadedData.newPatientsIds.toList().forEach {
            volleyRequestManager.getFullPatientById(it) {
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
     * @param downloadedData the data we received from the server
     */
    fun onFetchDataCompleted(downloadedData: DownloadedData)

    /**
     * called everytime we get a network result for all the upload network calls
     * @param uploadRequestStatus contains number of total requests, failed requests, success requests
     */
    fun onNewPatientAndReadingUploaded(uploadRequestStatus: UploadRequestStatus)

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
data class UploadRequestStatus(var totalNum: Int, var numFailed: Int, var numUploaded: Int) {
    fun allRequestCompleted(): Boolean {
        return (totalNum == numFailed + numUploaded)
    }
}
