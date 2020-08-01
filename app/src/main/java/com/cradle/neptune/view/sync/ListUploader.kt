package com.cradle.neptune.view.sync

import android.content.Context
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.NetworkResult
import com.cradle.neptune.network.Success
import javax.inject.Inject
import org.json.JSONObject

/**
 * Uploads a list of an object to the server. The list could be [Reading] or [Patient]
 * The idea is to get a list of patients/reading and upload them one by one until the list is empty.
 */
class ListUploader(
    val context: Context,
    private val uploadType: UploadType,
    private val listToUpload: ArrayList<*>,
    private val callerCallback: (isSuccessful: Boolean) -> Unit
) {

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    private var numUploaded = 0
    private var totalNum = 0

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
        // todo need to find a better way to do this, maybe split it into 2 sub classes
        totalNum = listToUpload.size
        if (uploadType == UploadType.READING) {
            uploadSingleReading()
        } else {
            uploadSinglePatient()
        }
    }

    private fun uploadSinglePatient() {
        if (listToUpload.isEmpty()) {
            return
        }
        volleyRequestManager.uploadPatientToTheServer(listToUpload[0] as Patient, networkCallback)
    }

    private fun uploadSingleReading() {
        if (listToUpload.isEmpty()) {
            return
        }
        volleyRequestManager.uploadReadingToTheServer(listToUpload[0] as Reading, networkCallback)
    }

    /**
     * Callback to continue uploading the readings one by one.
     */
    private val networkCallback: (NetworkResult<JSONObject>) -> Unit = { result ->
        when (result) {
            is Success -> {
                // upload progress
                numUploaded++
                callerCallback(true)
            }
            is Failure -> {
                callerCallback(false)
            }
        }
        listToUpload.removeAt(0)
        if (uploadType == UploadType.PATIENT) {
            uploadSinglePatient()
        } else {
            uploadSingleReading()
        }
    }

    /**
     * ENUM to decide what type of list to invoke
     */
    enum class UploadType(s: String) {
        PATIENT("Patients"), READING("Readings")
    }
}
