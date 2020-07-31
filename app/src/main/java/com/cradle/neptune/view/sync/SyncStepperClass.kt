package com.cradle.neptune.view.sync

import android.content.Context
import android.util.Log
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.toList
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
import com.cradle.neptune.view.sync.ListUploader.UploadType.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

/**
 * The idea of this class is to act as a stepper for the sync process and to let the calling object know of the process know when its done the step through a call back
 * than the caller will call next step.
 */
class SyncStepperClass(val context: Context, val stepperCallback: SyncStepperCallback) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager
    @Inject
    lateinit var patientManager:PatientManager
    @Inject
    lateinit var readingManager: ReadingManager

    lateinit var downloadedData: DownloadedData
    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }
    /**
     * step number 1, get all the newest data from the server. and go to step number 2.
     */
    fun fetchUpdatesFromServer(){
        // give a timestamp to provide
        val currTime = 1596091983L
        volleyRequestManager.getUpdates(currTime){ result ->
            when(result){
                is Success -> {
                    //result was success
                    Log.d("bugg",result.value.toString(4))
                     downloadedData = DownloadedData(result.value)
                    // let caller know of the next step
                    stepperCallback.onFetchDataCompleted(downloadedData)
                    GlobalScope.launch (Dispatchers.IO) {
                        val patientsToUpload = getPatientToUpload()
                        val readingsToUpload = ArrayList(readingManager.getUnUploadedReadings())
                        setupUploadingPatientReadings(patientsToUpload,readingsToUpload)
                    }
                }
                is Failure -> {
                    //let user know we failed.... probably cant continue?

                }
            }
        }
    }

    private suspend fun getPatientToUpload(): ArrayList<Patient> {

        val unUploadedPatients = ArrayList(patientManager.getAllPatients())

        unUploadedPatients.forEach {
            if (downloadedData.editedPatientId.contains(it.id) || downloadedData.newPatientsIds.contains(it.id)){
                unUploadedPatients.remove(it)
            }
        }
        return unUploadedPatients
    }

    /**
     * starts uploading readings and patients starting with patients.
     */
    private fun setupUploadingPatientReadings(patientsList: ArrayList<Patient>, readingList: ArrayList<Reading>) {

        // this will be all the new patients with their readings.
        ListUploader(context, PATIENT,patientsList){patientResult->
            //once finished call uploading a single patient, need to update the base time
            // going to put patient inside readings later on
            Log.d("bugg","starting to upload the readings")
        }
        //how do  i get the readings for existing patients??
        // these will be readings for existing patients that were not part
        ListUploader(context,  READING,readingList){readingResult->
            // finished uploading the readings and show the overall status.
            if (readingResult) {
                //let the caller know?
                stepperCallback.onNewPatientAndReadingUploaded()
                //startuploadingEdited
            }
        }

        // this will be edited patients that were not edited in the server, we match againt the downloaded object
        ListUploader(context, PATIENT,patientsList){

        }

        //todo once all the callbacks are done let server know and start next step.
    }

    /**
     * now we download all the information one by one
     */
    private fun downloadAllInfo(){
        //download brand new patients that are not on local
         downloadedData.newPatientsIds.toList().forEach {
             volleyRequestManager.getAllPatientsFromServerByQuery(it){

             }
         }
        // grab all the patients that we have but were edited by the server.... these include the patients we rejected to upload
        downloadedData.editedPatientId.toList().forEach {
            volleyRequestManager.getAllPatientsFromServerByQuery(it){

            }
        }

        downloadedData.newReadingsIds.toList().forEach {
            // get all the readings that were created for existing patients from the server
        }

        downloadedData.followUpIds.toList().forEach {
            // make a volley request to get the requests
        }

        // once all these are downloaded, let activity know the process has completed.
    }



}

/**
 * let activity know whats going on
 */
interface SyncStepperCallback {

    fun onFetchDataCompleted(downloadedData: DownloadedData)
    fun onNewPatientAndReadingUploaded()
    fun onStepThree()
    fun onStepFour()
}

data class DownloadedData(val jsonArray: JSONObject){
    val newPatientsIds = HashSet<String>(jsonArray.getJSONArray("newPatients").toList())
    val editedPatientId = HashSet<String>(jsonArray.getJSONArray("editedPatients").toList())
    // patients for these readings exists on the server so just download these readings...
    val newReadingsIds = HashSet<String>(jsonArray.getJSONArray("readings").toList())
    // followup for referrals that were sent through the phone
    val followUpIds = HashSet<String>(jsonArray.getJSONArray("followups").toList())
}