package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.navigation.Navigation
import com.cradle.neptune.R
import com.cradle.neptune.view.ReadingActivity

/**
 * Allow user to input vital signs, including OCR for data from the photo.
 */
class VitalSignsFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_vital_signs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.next_button)?.setOnClickListener {
            Toast.makeText(view.context, "Not implemented", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.cradle_vsa_take_photo_button)?.setOnClickListener(
            Navigation.createNavigateOnClickListener(
                R.id.action_vitalSignsFragment_to_cameraFragment,
                null
            )
        )
        view.findViewById<Button>(R.id.back_button)?.setOnClickListener {
            (activity as? ReadingActivity)?.onSupportNavigateUp()
        }
    }

    companion object {
        const val MANUAL_USER_ENTRY_SYSTOLIC = 1
        const val MANUAL_USER_ENTRY_DIASTOLIC = 2
        const val MANUAL_USER_ENTRY_HEARTRATE = 4

        /**
         * OCR
         */
        private const val OCR_DEBUG_IDS_SCALED_IDX = 0
        private const val OCR_DEBUG_IDS_RAW_IDX = 1
        private const val OCR_DEBUG_IDS_TEXT_IDX = 2

        fun newInstance(): VitalSignsFragment {
            return VitalSignsFragment()
        }
    }
}
