package com.cradle.neptune.view.sync

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    override fun onFetchDataCompleted(success:Boolean) {
        // maybe display it?
        Log.d("bugg","on activity fetch is completed")
        val iv = view?.findViewById<ImageView>(R.id.ivFetchStatus)
        if (success){
            iv?.setImageResource(R.drawable.arrow_right_with_check)
        } else {
            iv?.setImageResource(R.drawable.arrow_right_with_x)
        }

    }

    override fun onNewPatientAndReadingUploaded(totalRequestStatus: TotalRequestStatus) {
        // update the UI to show the progress
        Log.d("bugg","in the activity: total: "+ totalRequestStatus.totalNum + " num upload: "+
            totalRequestStatus.numUploaded+ " num failed: "+ totalRequestStatus.numFailed)
    }

    override fun onStepThree() {
        TODO("Not yet implemented")
    }

    override fun onStepFour() {
        TODO("Not yet implemented")
    }
}
