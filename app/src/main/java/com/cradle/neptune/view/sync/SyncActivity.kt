package com.cradle.neptune.view.sync

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R

class SyncActivity : AppCompatActivity(), SyncStepperCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync)
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
        // maybe display it?
        val iv = findViewById<ImageView>(R.id.ivFetchStatus)
        if (success) {
            iv.setImageResource(R.drawable.arrow_right_with_check)
        } else {
            iv.setImageResource(R.drawable.arrow_right_with_x)
        }
    }

    override fun onNewPatientAndReadingUploading(uploadStatus: TotalRequestStatus) {
        // update the UI to show the progress
        Log.d("bugg", "in the activity: total: " + uploadStatus.totalNum + " num upload: " +
            uploadStatus.numUploaded + " num failed: " + uploadStatus.numFailed)
    }

    override fun onNewPatientAndReadingDownloading(downloadStatus: TotalRequestStatus) {

    }

    override fun onFinish() {

    }
}
