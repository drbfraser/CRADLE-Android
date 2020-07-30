package com.cradle.neptune.view.sync

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.cradle.neptune.R

class SyncResultFragment: Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sync_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUploadingPatientReadings()
    }

    /**
     * starts uploading readings and patients starting with patients.
     */
    private fun setupUploadingPatientReadings() {
        var tv: TextView? = view?.findViewById(R.id.patientUploadMessage)
        var errorTV = view?.findViewById<TextView>(R.id.uploadPatientErrorMessage)

        MultiUploader(requireContext(),tv,errorTV,MultiUploader.UploadType.PATIENT){patientResult->
            //once finished call uploading the patients
             tv  = view?.findViewById(R.id.readingUploadMessage)
             errorTV = view?.findViewById(R.id.uploadReadingErrorMessage)
            Log.d("bugg","starting to upload the readings")
            MultiUploader(requireContext(), tv,errorTV, MultiUploader.UploadType.READING){readingResult->
                // finished uploading the readings and show the overall status.
                val iv: ImageView? = view?.findViewById<ImageView>(R.id.ivUploadAction)
                if (readingResult &&patientResult) {
                    iv?.setImageResource(R.drawable.arrow_right_with_check)
                } else {
                    iv?.setImageResource(R.drawable.arrow_right_with_x)
                }
            }
        }

    }
}