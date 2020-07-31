package com.cradle.neptune.view.sync

import android.content.Context
import android.widget.TextView
import com.android.volley.VolleyError
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.NetworkResult
import com.cradle.neptune.network.Success
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Uploads a list of an object to the server. The list could be [Reading] or [Patient]
 * The idea is to get a list of patients/reading and upload them one by one until the list is empty.
 */
class ListUploader(val context: Context, private val uploadType:UploadType,private val listToUpload: ArrayList<*>,
    private val finishCallback: (isSuccessful:Boolean)->Unit) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private var numUploaded = 0
    private var totalNum =0

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
        //todo need to find a better way to do this, maybe split it into 2 sub classes
        totalNum =listToUpload.size
        if (uploadType==UploadType.READING){
            uploadSingleReading()
        } else {
            uploadSinglePatient()
        }
    }

    private fun uploadSinglePatient(){
        if (listToUpload.isEmpty()){
            finishCallback.invoke(isUploadSuccess())
            return
        }
        volleyRequestManager.uploadPatientToTheServer(listToUpload[0] as Patient,networkCallback)
    }

    private fun uploadSingleReading(){
        if (listToUpload.isEmpty()){
            finishCallback.invoke(isUploadSuccess())
            return
        }
        volleyRequestManager.uploadReadingToTheServer(listToUpload[0] as Reading,networkCallback)
    }

    /**
     * Callback to continue uploading the readings one by one.
     */
    private val networkCallback:(NetworkResult<JSONObject>) -> Unit = {result ->
        when(result) {
            is Success -> {
                // upload progress
                numUploaded++
            }
            is Failure -> {
            }
        }
        listToUpload.removeAt(0)
        if (uploadType == UploadType.PATIENT){
            uploadSinglePatient()
        } else {
            uploadSingleReading()
        }

    }

    /**
     * parses [VolleyError] to get a readable error message.  todo find a better place for this.
     */
    private fun getServerErrorMessage(error: VolleyError): String {
        var message = "Unable to upload to server (network error)"
        when {
            error.cause != null -> {
                message = when (error.cause) {
                    UnknownHostException::class.java -> {
                        "Unable to resolve server address; check server URL in settings."
                    }
                    ConnectException::class.java -> {
                        "Cannot reach server; check network connection."
                    }
                    else -> {
                        error.cause?.message.toString()
                    }
                }
            }
            error.networkResponse != null -> {
                message = when (error.networkResponse.statusCode) {
                    401 -> "Server rejected credentials; check they are correct in settings."
                    400 -> "Server rejected upload request; check server URL in settings."
                    404 -> "Server rejected URL; check server URL in settings."
                    else -> "Server rejected upload; check server URL in settings. Code " + error.networkResponse.statusCode
                }
            }
        }
        return message
    }

    private fun isUploadSuccess():Boolean{
        return (numUploaded>0 && (numUploaded==totalNum))
    }
    enum class UploadType(s: String) {
        PATIENT("Patients"),READING("Readings")
    }
}