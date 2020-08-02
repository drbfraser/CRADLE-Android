package com.cradle.neptune.view.sync

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import javax.inject.Inject

class SyncResultFragment : Fragment(), SyncStepperCallback {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as MyApp).appComponent.inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sync_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SyncStepperClass(requireContext(), this).fetchUpdatesFromServer(sharedPreferences.getLong("lastSyncTime", 0))
    }

    override fun onFetchDataCompleted(success: Boolean) {
        // maybe display it?
        val iv = view?.findViewById<ImageView>(R.id.ivFetchStatus)
        if (success) {
            iv?.setImageResource(R.drawable.arrow_right_with_check)
        } else {
            iv?.setImageResource(R.drawable.arrow_right_with_x)
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
