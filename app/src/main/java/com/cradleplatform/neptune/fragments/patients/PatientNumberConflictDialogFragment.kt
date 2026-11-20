package com.cradleplatform.neptune.fragments.patients

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
import com.cradleplatform.neptune.activities.newPatient.ReadingActivity
import com.cradleplatform.neptune.viewmodel.patients.PatientReadingViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/**
 * The Dialog that shows when creating a patient with a patient number that a patient in the user's
 * local database already has. Patient numbers don't have to be unique, so the user can either
 * switch over to adding a new reading for the existing patient, or create the new patient anyway.
 */
@AndroidEntryPoint
class PatientNumberConflictDialogFragment : DialogFragment() {
    private val viewModel: PatientReadingViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(context is ReadingActivity)
        check(arguments != null)
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
        val patient = arguments?.getSerializable(EXTRA_PATIENT) as Patient?
            ?: error("missing ${Companion::EXTRA_PATIENT.name}")

        val message = getString(
            R.string.reading_activity_patient_number_exists_dialog_message,
            patient.patientNumber,
            patient.name,
            Converter.sexToString(requireActivity(), patient.sex)
        )

        return MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.reading_activity_patient_number_exists_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.reading_activity_patient_number_exists_dialog_use_button) { _, _ ->
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
            }
            .setNeutralButton(R.string.reading_activity_patient_number_exists_dialog_create_button) { _, _ ->
                (requireActivity() as ReadingActivity).continueWithDuplicatePatientNumber()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.setInputEnabledState(true)
            }
            .show()
    }

    companion object {
        private const val EXTRA_PATIENT = "extra_patient"

        fun makeInstance(patient: Patient) =
            PatientNumberConflictDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_PATIENT, patient)
                }
            }
    }
}
