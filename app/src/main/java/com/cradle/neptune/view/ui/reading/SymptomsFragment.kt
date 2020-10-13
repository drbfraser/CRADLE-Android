package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.lifecycle.observe
import com.cradle.neptune.R

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val checkBoxContainer = view.findViewById<LinearLayout>(R.id.symptoms_checkbox_container)
            ?: error("no container")

        val checkBoxes = view.resources.getStringArray(R.array.reading_symptoms)
            .mapIndexed { index, symptomString ->
                CheckBox(checkBoxContainer.context)
                    .apply {
                        text = symptomString
                        setOnCheckedChangeListener { _, isChecked ->
                            viewModel.setSymptomsState(index, isChecked)
                        }
                    }
                    .also { checkBoxContainer.addView(it) }
            }
            .toList()

        viewModel.symptomsState.observe(viewLifecycleOwner) { newSymptomsState ->
            checkBoxes.forEachIndexed { index, checkBox ->
                val newState = newSymptomsState.isSymptomIndexChecked(index)
                if (newState != checkBox.isChecked) {
                    checkBox.isChecked = newState
                }
            }
        }
    }
}
