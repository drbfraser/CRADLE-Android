package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cradle.neptune.R

/**
 * Allow user to confirm data from the CRADLE photo.
 */
@Suppress("LargeClass")
class ConfirmDataFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_confirm_data, container, false)
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
        @Suppress("ObjectPropertyNaming")
        private val OCR_DEBUG_IDS = arrayOf(
            intArrayOf(R.id.ivOcrScaled0, R.id.ivOcrRaw0, R.id.tvOcrText0),
            intArrayOf(R.id.ivOcrScaled1, R.id.ivOcrRaw1, R.id.tvOcrText1),
            intArrayOf(R.id.ivOcrScaled2, R.id.ivOcrRaw2, R.id.tvOcrText2)
        )

        fun newInstance(): ConfirmDataFragment {
            return ConfirmDataFragment()
        }
    }
}
