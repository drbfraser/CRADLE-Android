package com.cradle.neptune.view.ui.reading

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import com.cradle.neptune.R
import com.cradle.neptune.binding.Converter
import com.cradle.neptune.model.Patient
import com.cradle.neptune.view.ReadingActivity
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class PatientIdInUseDialogFragment : DialogFragment() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var viewModelFactory: PatientReadingViewModelFactory

    private val viewModel: PatientReadingViewModel by activityViewModels {
        viewModelFactory
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
        check(context is ReadingActivity)
        check(arguments != null)
        check(arguments?.get(EXTRA_IS_PATIENT_LOCAL) != null)
        check(arguments?.get(EXTRA_PATIENT) != null)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        dialog?.apply {
            setOnDismissListener { viewModel.setInputEnabledState(true) }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.setInputEnabledState(false)
        val isPatientInPatientsList = arguments?.getBoolean(EXTRA_IS_PATIENT_LOCAL)
            ?: error("missing ${Companion::EXTRA_IS_PATIENT_LOCAL.name}")
        val patient = arguments?.getSerializable(EXTRA_PATIENT) as Patient?
            ?: error("missing ${Companion::EXTRA_PATIENT.name}")

        val title = getString(
            if (isPatientInPatientsList) {
                R.string.reading_activity_patient_id_exists_dialog_title_local_patient
            } else {
                R.string.reading_activity_patient_id_exists_dialog_title_server_patient
            }
        )
        val message = getString(
            if (isPatientInPatientsList) {
                R.string.reading_activity_patient_id_exists_dialog_message_local_patient
            } else {
                R.string.reading_activity_patient_id_exists_dialog_message_server_patient
            },
            patient.id,
            patient.name,
            Converter.sexToString(requireActivity(), patient.sex)
        )
        val positiveButtonLabel = getString(
            if (isPatientInPatientsList) {
                R.string.reading_activity_patient_id_exists_dialog_use_button_local_patient
            } else {
                R.string.reading_activity_patient_id_exists_dialog_download_button_server_patient
            }
        )

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonLabel) { _, _ ->
                if (isPatientInPatientsList) {
                    // TODO: use Navigation and make a deep link to the patient's profile
                    val newReadingIntent =
                        ReadingActivity.makeIntentForNewReadingExistingPatientUsingPatientId(
                            context = requireContext(), patientId = patient.id
                        )
                    (requireActivity() as ReadingActivity).apply {
                        startActivity(newReadingIntent)
                        finish()
                    }
                } else {
                    // todo: download
                    Toast.makeText(requireContext(), "TODO: Download patient", Toast.LENGTH_SHORT)
                        .show()
                    (requireActivity() as ReadingActivity)
                        .findNavController(R.id.reading_nav_host)
                        .navigate(R.id.action_patientInfoFragment_to_symptomsFragment)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.setInputEnabledState(true)
            }
            .show()
    }

    companion object {
        private const val EXTRA_PATIENT = "extra_patient"
        private const val EXTRA_IS_PATIENT_LOCAL = "is_patient_local_only"

        fun makeInstance(isPatientLocal: Boolean, patient: Patient) =
            PatientIdInUseDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_PATIENT, patient)
                    putBoolean(EXTRA_IS_PATIENT_LOCAL, isPatientLocal)
                }
            }
    }
}
