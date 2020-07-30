package com.cradle.neptune.view.ui.network_volley

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.VolleyError
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.MarshalManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.Util
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Handle uploading multiple readings to the server.
 * Interface with client code (an activity) via callbacks.
 */
class MultiReadingUploader(val context: Context, private val progressCallback: ProgressCallback) {
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var marshalManager: MarshalManager
    @Inject
    lateinit var urlManager: UrlManager

    private var state =
        State.IDLE

    // info
    var numCompleted = 0
        private set

    // OPERATIONS
    fun startUpload(readings: MutableList<Reading>) {
        if (state != State.IDLE) {
            Log.e(TAG, "ERROR: Not in idle state")
        } else {
            Util.ensure(readings.size > 0)
            startUploadOfPendingReading()
            progressCallback.uploadProgress(numCompleted, totalNumReadings)
        }
    }

    // State
    val isUploadingReadingToStart: Boolean
        get() = state == State.IDLE

    val isUploading: Boolean
        get() = state == State.UPLOADING

    val isUploadPaused: Boolean
        get() = state == State.PAUSED

    val isUploadDone: Boolean
        get() = state == State.DONE



    val totalNumReadings: Int
        get() = numCompleted
    // INTERNAL STATE MACHINE OPERATIONS
    private fun startUploadOfPendingReading() {
    }

    // handle aborted upload:

    // current reading uploaded successfully

    // advance to next reading
    private val successCallback: Response.Listener<NetworkResponse> = Response.Listener {
    }

    // handle aborted upload:

    // error uploading current reading
    private val errorCallback: Response.ErrorListener= Response.ErrorListener { error: VolleyError? ->
            // handle aborted upload:
            if (state == State.DONE) {
                return@ErrorListener
            }
            state = State.PAUSED

            // error uploading current reading
            var message: String? = "Unable to upload to server (network error)"
            when {
                error == null -> {
                    // do nothing special
                }
                error.cause != null -> {
                    message = if (error.cause == UnknownHostException::class.java) {
                        "Unable to resolve server address; check server URL in settings."
                    } else if (error.cause == ConnectException::class.java) {
                        "Cannot reach server; check network connection."
                    } else {
                        error.cause?.message
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
            progressCallback.uploadPausedOnError(message)
        }

    private enum class State {
        IDLE, UPLOADING, PAUSED, DONE
    }

    interface ProgressCallback {
        fun uploadProgress(numCompleted: Int, numTotal: Int)
        fun uploadReadingSucceeded(r: Pair<Patient?, Reading?>?)
        fun uploadPausedOnError(message: String?)
    }

    companion object {
        private const val TAG = "MultiReadingUploader"
    }

    init {
        (context.applicationContext as MyApp).appComponent.inject(this)
    }
}