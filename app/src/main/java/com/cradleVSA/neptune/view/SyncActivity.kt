package com.cradleVSA.neptune.view

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.sync.SyncStepper
import com.cradleVSA.neptune.sync.SyncStepperCallback
import com.cradleVSA.neptune.sync.TotalRequestStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

/**
 * TODO: redesign
 */
@AndroidEntryPoint
class SyncActivity : AppCompatActivity(), SyncStepperCallback {

    companion object {
        private const val TAG = "SyncActivity"
        private const val NUM_STEPS_FOR_SYNC = 3.0
    }

    private val syncStepper = SyncStepper(this@SyncActivity, this@SyncActivity)

    private lateinit var uploadStatusTextView: TextView
    private lateinit var downloadStatusTextView: TextView

    private lateinit var syncText: TextView

    private val progressPercent =
        ProgressPercent(NUM_STEPS_FOR_SYNC)
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

        uploadStatusTextView = findViewById(R.id.uploadStatusTxt)
        downloadStatusTextView = findViewById(R.id.downloadStatusTxt)

        progressBar = findViewById(R.id.syncProgressBar)
        syncText = findViewById(R.id.syncText)

        setSupportActionBar(findViewById(R.id.toolbar2))
        supportActionBar?.title = getString(R.string.sync_activity_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.uploadEverythingButton).setOnClickListener {
            syncText.text = getString(R.string.sync_activity_sync_progress_text)
            progressBar.visibility = View.VISIBLE
            // TODO: launch this in the background as a service or use WorkManager.
            //  User shouldn't be expected to leave the screen on and keep this
            //  activity on to sync. MainScope() also doesn't get cancelled properly.
            MainScope().launch {
                it.visibility = View.GONE
                val totalTimeTaken = measureTimeMillis { syncStepper.doSync(this@SyncActivity) }
                Log.d(TAG, "DEBUG: Total sync time taken: $totalTimeTaken")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onFetchDataCompleted(success: Boolean) {
        progressPercent.current++
        setProgressPercent()
    }

    override fun onNewPatientAndReadingUploading(uploadStatus: TotalRequestStatus) {
        uploadStatusTextView.text = getString(
            R.string.sync_activity_upload_status,
            uploadStatus.numUploaded + uploadStatus.numFailed,
            uploadStatus.totalNum
        )
    }

    override fun onNewPatientAndReadingDownloading(downloadStatus: TotalRequestStatus) {
        downloadStatusTextView.text =
            "Request completed ${downloadStatus.numUploaded + downloadStatus.numFailed} " +
            "out of  ${downloadStatus.totalNum}"
    }

    override fun onNewPatientAndReadingUploadFinish(uploadStatus: TotalRequestStatus) {
        progressPercent.current++
        uploadStatusTextView.text =
            "Successfully made ${uploadStatus.numUploaded} out of ${uploadStatus.totalNum} requests"
        setStatusArrow(uploadStatus.allRequestsSuccess(), R.id.ivUploadStatus)
        setProgressPercent()
    }

    override fun onNewPatientAndReadingDownloadFinish(downloadStatus: TotalRequestStatus) {
        progressPercent.current++
        // TODO: Extract strings
        downloadStatusTextView.text =
            "Successfully made  ${downloadStatus.numUploaded} out of ${downloadStatus.totalNum} requests"
        setStatusArrow(downloadStatus.allRequestsSuccess(), R.id.ivDownloadStatus)
        setProgressPercent()
    }

    override fun onFinish(errorCodes: HashMap<Int?, String?>) {
        progressBar.visibility = View.INVISIBLE

        if (errorCodes.isEmpty()) {
            syncText.text = getString(R.string.sync_activity_sync_complete)
        } else {
            syncText.setTextColor(resources.getColor(R.color.error))
            val stringBuilder = StringBuilder()
            errorCodes.entries.forEach {
                stringBuilder.append(getString(R.string.sync_activity_error_line, it.value))
                    .append("\n")
            }
            syncText.text = stringBuilder.toString()
        }
    }

    private fun setStatusArrow(success: Boolean, id: Int) {
        val iv = findViewById<ImageView>(id)
        if (success) {
            iv.setImageResource(R.drawable.arrow_right_with_check)
        } else {
            iv.setImageResource(R.drawable.arrow_right_with_x)
        }
    }

    private fun setProgressPercent() {
        syncText.text = getString(
            R.string.sync_activity_sync_progress_percent,
            progressPercent.getPercent().roundToInt()
        )
    }
}

/**
 * Very simple class to get overall progress percent
 */
data class ProgressPercent(var overall: Double) {
    var current: Double = 0.0

    fun getPercent(): Double {
        return current / overall * DECIMAL_TO_PERCENT
    }

    companion object {
        const val DECIMAL_TO_PERCENT = 100
    }
}
