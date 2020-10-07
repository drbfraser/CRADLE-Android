package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.lifecycle.observe
import com.cradle.neptune.R
import com.cradle.neptune.model.Sex
import com.cradle.neptune.utilitiles.unreachable
import com.google.android.material.textfield.TextInputEditText

private const val GA_UNIT_INDEX_WEEKS = 0
private const val GA_UNIT_INDEX_MONTHS = 1

private const val PATIENT_SEX_MALE = 0
private const val PATIENT_SEX_FEMALE = 1
private const val PATIENT_SEX_OTHER = 2

/**
 * Logic for the UI fragment which collects patient information when creating
 * or updating a reading.
 */
@Suppress("LargeClass")
class PatientInfoFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.fragment_patient_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.patientId.observe(viewLifecycleOwner) {
            view.findViewById<TextInputEditText>(R.id.patient_id_text).setText(it)
        }

    }

    companion object {
        private const val DatePickerDefaultYear = 2000
    }
}

/**
 * Converts [sex] into an index.
 *
 * We use this function instead of [Sex.ordinal] because, in the event that
 * someone re-orders or adds a new [Sex] variant, the UI would silently break.
 */
private fun sexIndex(sex: Sex): Int = when (sex) {
    Sex.MALE -> PATIENT_SEX_MALE
    Sex.FEMALE -> PATIENT_SEX_FEMALE
    Sex.OTHER -> PATIENT_SEX_OTHER
}

/**
 * Converts an index into a [Sex] variant.
 */
private fun sexFromIndex(index: Int): Sex = when (index) {
    PATIENT_SEX_MALE -> Sex.MALE
    PATIENT_SEX_FEMALE -> Sex.FEMALE
    PATIENT_SEX_OTHER -> Sex.OTHER
    else -> unreachable("illegal sex index")
}

private fun View.editText(id: Int): String = this
    .findViewById<EditText>(id)
    .text
    .toString()
    .trim()
