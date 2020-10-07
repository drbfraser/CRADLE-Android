package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cradle.neptune.R

private const val TAG = "SymptomsFragment"

/**
 * Gather information about the patient.
 */
@Suppress("LargeClass", "EmptyFunctionBlock")
class SymptomsFragment : BaseFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_symptoms, container, false)
    }
}
