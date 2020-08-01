package com.cradle.neptune.view.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cradle.neptune.R

class SyncResultFragment : Fragment(), SyncStepperCallback {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sync_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SyncStepperClass(requireContext(), this).fetchUpdatesFromServer()
    }

    override fun onFetchDataCompleted(downloadedData: DownloadedData) {
        // maybe display it?
    }

    override fun onNewPatientAndReadingUploaded(uploadRequestStatus: UploadRequestStatus) {
        // update the UI to show the progress
    }

    override fun onStepThree() {
        TODO("Not yet implemented")
    }

    override fun onStepFour() {
        TODO("Not yet implemented")
    }
}
