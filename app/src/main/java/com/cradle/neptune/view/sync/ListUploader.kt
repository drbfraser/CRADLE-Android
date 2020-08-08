package com.cradle.neptune.view.sync

import android.content.Context
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.NetworkResult
import com.cradle.neptune.network.Success
import javax.inject.Inject
import org.json.JSONObject

/**
 * Uploads a list of an object to the server. The list could be [Reading] or [Patient]
 * The idea is to get a list of patients/reading and upload them one by one until the list is empty.
 * [callerCallback] lets the caller know boolean status of every call: success/fail
 */
class ListUploader(
    val context: Context,
    private val uploadType: UploadType,
    private val listToUpload: ArrayList<*>,
    private val callerCallback: (NetworkResult<JSONObject>) -> Unit
) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private var numUploaded = 0
    private var totalNum = 0

    private var isUploadStarted = false

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
        totalNum = listToUpload.size
    }

    /**
     * function to the caller to control when to start uploading
     */
    fun startUpload() {
        if (!isUploadStarted) {
            uploadSingleObject()
            isUploadStarted = true
        }
    }

    /**
     * uploads a single object from the list.
     */
    private fun uploadSingleObject() {
        if (listToUpload.isEmpty()) {
            return
        }
        if (uploadType == UploadType.PATIENT) {
            volleyRequestManager.uploadPatientToTheServer(
                listToUpload[0] as PatientAndReadings,
                networkCallback
            )
        } else {
            volleyRequestManager.uploadReadingToTheServer(
                listToUpload[0] as Reading,
                networkCallback
            )
        }
    }

    /**
     * Callback to continue uploading the objects one by one.
     */
    private val networkCallback: (NetworkResult<JSONObject>) -> Unit = { result ->
        when (result) {
            is Success -> {
                // upload progress
                numUploaded++
                callerCallback(Success(result.value))
            }
            is Failure -> {
                callerCallback(Failure(result.value))
            }
        }
        // remove the object we got result for
        listToUpload.removeAt(0)
        // upload next one
        uploadSingleObject()
    }

    /**
     * ENUM to decide what type of list to invoke
     */
    enum class UploadType(s: String) {
        PATIENT("Patients"), READING("Readings")
    }
}
