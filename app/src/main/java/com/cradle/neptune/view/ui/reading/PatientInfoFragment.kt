package com.cradle.neptune.view.ui.reading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.observe
import com.cradle.neptune.R
import com.cradle.neptune.databinding.FragmentPatientInfoBinding
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Sex
import com.cradle.neptune.utilitiles.unreachable

private const val TAG = "PatientInfoFragment"

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

    var binding: FragmentPatientInfoBinding? = null

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val dataBinding = DataBindingUtil.inflate<FragmentPatientInfoBinding>(
            inflater,
            R.layout.fragment_patient_info,
            container,
            false
        ).apply {
            executePendingBindings()
            viewModel = this@PatientInfoFragment.viewModel
        }
        binding = dataBinding
        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.patientId.observe(viewLifecycleOwner) {
            viewModel.handleEditTextErrors(
                rootView = view, resId = R.id.patient_id_text, value = it,
                isForPatient = true, property = Patient::id
            )
        }
        viewModel.patientName.observe(viewLifecycleOwner) {
            viewModel.handleEditTextErrors(
                view, resId = R.id.initials_text, value = it,
                isForPatient = true, property = Patient::name
            )
        }
        viewModel.patientZone.observe(viewLifecycleOwner) {
            viewModel.handleEditTextErrors(
                view, resId = R.id.zone_text, value = it,
                isForPatient = true, property = Patient::zone
            )
        }
        viewModel.patientVillageNumber.observe(viewLifecycleOwner) {
            viewModel.handleEditTextErrors(
                view, resId = R.id.village_text, value = it,
                isForPatient = true, property = Patient::villageNumber
            )
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
