package com.cradle.neptune.view.sync

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R
import java.lang.StringBuilder

class SyncActivity : AppCompatActivity(), SyncStepperCallback {

    private lateinit var uploadStatusTextView: TextView
    private lateinit var downloadStatusTextView: TextView

    lateinit var progressBar: ProgressBar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)

        uploadStatusTextView = findViewById(R.id.uploadStatusTxt)
        downloadStatusTextView = findViewById(R.id.downloadStatusTxt)
        progressBar = findViewById(R.id.syncProgressBar)

        setSupportActionBar(findViewById(R.id.toolbar2))
        supportActionBar?.title = "Sync Everything"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.uploadEverythingButton).setOnClickListener {
            SyncStepperImplementation(this, this).fetchUpdatesFromServer()
            it.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    @Synchronized
    override fun onFetchDataCompleted(success: Boolean) {
        setStatusArrow(success, R.id.ivFetchStatus)
        progressBar.progress++
    }

    @Synchronized
    override fun onNewPatientAndReadingUploading(uploadStatus: TotalRequestStatus) {
            uploadStatusTextView.text =
                "Request completed ${uploadStatus.numUploaded + uploadStatus.numFailed}" +
                    " out of  ${uploadStatus.totalNum}"
    }

    @Synchronized
    override fun onNewPatientAndReadingDownloading(downloadStatus: TotalRequestStatus) {
            downloadStatusTextView.text =
                "Request completed ${downloadStatus.numUploaded + downloadStatus.numFailed} " +
                    "out of  ${downloadStatus.totalNum}"
    }

    @Synchronized
    override fun onNewPatientAndReadingUploadFinish(uploadStatus: TotalRequestStatus) {
            uploadStatusTextView.text =
                "Successfully made ${uploadStatus.numUploaded} out of ${uploadStatus.totalNum} requests"
            setStatusArrow(uploadStatus.allRequestsSuccess(), R.id.ivUploadStatus)
            progressBar.progress++
    }
    @Synchronized
    override fun onNewPatientAndReadingDownloadFinish(downloadStatus: TotalRequestStatus) {
            downloadStatusTextView.text =
                "Successfully made  ${downloadStatus.numUploaded} out of ${downloadStatus.totalNum} requests"
            setStatusArrow(downloadStatus.allRequestsSuccess(), R.id.ivDownloadStatus)
            progressBar.progress++
    }

    @Synchronized
    override fun onFinish(errorCodes: HashMap<Int?, String>) {
            val textView = findViewById<TextView>(R.id.syncText)
            if (errorCodes.isEmpty()) {
                textView.text = "Sync complete"
            } else {
                textView.setTextColor(resources.getColor(R.color.error))
                val stringBuilder = StringBuilder()
                errorCodes.entries.forEach {
                    stringBuilder.append("Error: ").append(it.value).append("\n")
                }
                textView.text = stringBuilder.toString()
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
}
