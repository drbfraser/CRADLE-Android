package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cradle.neptune.R

/**
 * Display summary and advice for currentReading.
 */
@Suppress("LargeClass")
class SummaryFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_summary, container, false)
    }

    companion object {
        const val STROKE_WIDTH_RECOMMENDED = 6
        const val STROKE_WIDTH_NORMAL = 3
        var NUM_SECONDS_IN_15_MIN: Long = 900
        fun newInstance(): SummaryFragment {
            return SummaryFragment()
        }
    }
}
