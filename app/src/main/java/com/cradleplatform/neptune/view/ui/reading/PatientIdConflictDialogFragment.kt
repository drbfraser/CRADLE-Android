package com.cradleplatform.neptune.view.ui.reading

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.Converter
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.PatientReadingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/**
 * The Dialog that shows when attempting to use a patient ID that is already being used, either
 * on the user's local database or on the server. The dialog handles switching over to adding
 * a new reading for that patient, and it will also handle downloads in the case where the
 * patient was found on the server.
 */
@AndroidEntryPoint
class PatientIdConflictDialogFragment : DialogFragment() {
    private val viewModel: PatientReadingViewModel by activityViewModels()

    override fun onAttach(context: Context) {
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
                    // TODO: use Navigation and make a deep link to the patient's profile (refer to issue #34)
                    val newReadingIntent =
                        ReadingActivity.makeIntentForNewReadingExistingPatient(
                            context = requireContext(),
                            patientId = patient.id
                        )
                    (requireActivity() as ReadingActivity).apply {
                        startActivity(newReadingIntent)
                        finish()
                    }
                } else {
                    (requireActivity() as ReadingActivity).downloadPatientAndReadingsFromServer(
                        patient.id
                    )
                    dismissAllowingStateLoss()
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
            PatientIdConflictDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_PATIENT, patient)
                    putBoolean(EXTRA_IS_PATIENT_LOCAL, isPatientLocal)
                }
            }
    }
}
