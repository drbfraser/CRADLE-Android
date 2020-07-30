package com.cradle.neptune.view.sync

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.cradle.neptune.R

class SyncResultFragment: Fragment() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        setupUploadingReadings()
    }

    private fun setupUploadingReadings() {
        val tv: TextView? = view?.findViewById(R.id.tvUploadMessage)
        val errorTV = view?.findViewById<TextView>(R.id.tvUploadErrorMessage)

        val readingUploader = MultiUploader(requireContext(),tv,errorTV){
            //once finished call uploading the patients
        }
        readingUploader.uploadReadings()
    }
}