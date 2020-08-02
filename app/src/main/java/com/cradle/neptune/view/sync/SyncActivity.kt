package com.cradle.neptune.view.sync

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class SyncActivity : AppCompatActivity(), SyncStepperCallback {

    lateinit var uploadStatusTextView:TextView
    lateinit var downloadStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)
        uploadStatusTextView = findViewById(R.id.uploadStatusTxt)
        downloadStatusTextView = findViewById(R.id.downloadStatusTxt)

        setSupportActionBar(findViewById(R.id.toolbar2))
        supportActionBar?.title = "Sync Everything"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<Button>(R.id.uploadEverythingButton).setOnClickListener {
            SyncStepperClass(this, this).fetchUpdatesFromServer()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onFetchDataCompleted(success: Boolean) {
        setStatusArrow(success,R.id.ivFetchStatus)
    }

    override fun onNewPatientAndReadingUploading(uploadStatus: TotalRequestStatus) {
        uploadStatusTextView.text =
            "Total: ${uploadStatus.totalNum} Uploaded: ${uploadStatus.numUploaded}  Failed: ${uploadStatus.numFailed} ..."
    }

    override fun onNewPatientAndReadingDownloading(downloadStatus: TotalRequestStatus) {
        downloadStatusTextView.text =
            "Total: ${downloadStatus.totalNum} Uploaded: ${downloadStatus.numUploaded}  Failed: ${downloadStatus.numFailed}"
    }

    override fun onNewPatientAndReadingUploadFinish(uploadStatus: TotalRequestStatus) {
        MainScope().launch {
            uploadStatusTextView.text =
                "Successfully made ${uploadStatus.numUploaded} out of ${uploadStatus.totalNum} requests"
            setStatusArrow(uploadStatus.allRequestsSuccess(), R.id.ivUploadStatus)
        }

    }
    override fun onNewPatientAndReadingDownloadFinish(downloadStatus: TotalRequestStatus) {
        MainScope().launch {
            downloadStatusTextView.text =
                "Successfully made  ${downloadStatus.numUploaded} out of ${downloadStatus.totalNum} requests"
            setStatusArrow(downloadStatus.allRequestsSuccess(), R.id.ivDownloadStatus)
        }
    }

    override fun onFinish() {
    }

    private fun setStatusArrow(success: Boolean, id: Int){
        val iv = findViewById<ImageView>(id)
        if (success) {
            iv.setImageResource(R.drawable.arrow_right_with_check)
        } else {
            iv.setImageResource(R.drawable.arrow_right_with_x)
        }
    }
}
