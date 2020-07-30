package com.cradle.neptune.view.sync

import android.content.Context
import android.widget.TextView
import androidx.print.PrintHelper
import com.android.volley.VolleyError
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.VolleyRequestManager
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

class MultiUploader(val context: Context, private val progressTextView:TextView?, private val errorTextView: TextView?,val finishCallback: ()->Unit) {
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private lateinit var listToUpload: ArrayList<*>
    private var numUploaded = 0
    private var totalNum =0

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }
     fun uploadReadings(){
        GlobalScope.launch {
            listToUpload = ArrayList(readingManager.getUnUploadedReadings())
            totalNum =listToUpload.size
            uploadSingleReading()
        }
    }

    private fun uploadSingleReading(){
        if (listToUpload.isEmpty()){
            progressTextView?.text = "Readings uploaded: $numUploaded out of $totalNum"

            return
        }
        volleyRequestManager.uploadReadingToTheServer(listToUpload[0] as Reading,networkCallback)
    }


    private val networkCallback:(NetworkResult<JSONObject>) -> Unit = {result ->
        when(result) {
            is Success -> {
                // upload progress
                numUploaded++

                progressTextView?.text = "Uploading reading $numUploaded of $totalNum..."
            }
            is Failure -> {
                errorTextView?.text = "Error when uploading reading: \r\n${getServerErrorMessage(result.value)}"

            }
        }
        listToUpload.removeAt(0)
        uploadSingleReading()

    }



    private fun getServerErrorMessage(error: VolleyError): String {
        var message: String = "Unable to upload to server (network error)"
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
}