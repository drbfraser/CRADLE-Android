package com.cradle.neptune.view.sync

import android.content.Context
import android.util.Log
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * The idea of this class is to act as a stepper for the sync process and to let the calling object know of the process know when its done the step through a call back
 * than the caller will call next step.
 */
class SyncStepperClass(context: Context, val stepperCallback: SyncStepperCallback) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private lateinit var downloadedData: DownloadedData

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
                }
                is Failure -> {
                    //let user know we failed.... probably cant continue?

                }
            }
        }
    }

}

interface SyncStepperCallback {

    fun onFetchDataCompleted(downloadedData: DownloadedData)
    fun onStepTwo()
    fun onStepThree()
    fun onStepFour()
}

data class DownloadedData(val jsonArray: JSONObject){
    val newPatientsIds = jsonArray.getJSONArray("newPatients")
    val editedPatientId = jsonArray.getJSONArray("editedPatients")
    val newReadingsIds = jsonArray.getJSONArray("readings")
    val followUpIds:JSONArray = jsonArray.getJSONArray("followups")
}