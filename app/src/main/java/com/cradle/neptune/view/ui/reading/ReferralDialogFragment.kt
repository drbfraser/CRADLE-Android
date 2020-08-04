package com.cradle.neptune.view.ui.reading

import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.ReferralUploadManger
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Referral
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
import com.cradle.neptune.network.VolleyRequests
import com.cradle.neptune.utilitiles.UnixTimestamp
import com.cradle.neptune.view.ui.settings.SettingsActivity
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReferralDialogFragment(private val viewModel: PatientReadingViewModel) : DialogFragment() {

    companion object {
        fun makeInstance(viewModel: PatientReadingViewModel): ReferralDialogFragment {
            return ReferralDialogFragment(viewModel)
        }
    }

    /**
     * Called after a reading has been successfully uploaded via either web or SMS.
     */
    var onSuccessfulUpload: () -> Unit = { }

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var readingManager: ReadingManager

    @Inject
    lateinit var healthCentreManager: HealthCentreManager

    @Inject
    lateinit var referralUploadManger: ReferralUploadManger

    private lateinit var dialogView: View
    private lateinit var alertDialog: AlertDialog

    private val commentBox: EditText?
        get() = alertDialog.findViewById(R.id.referralCommentEditBox)

    private val healthFacilitySpinner: Spinner?
        get() = alertDialog.findViewById(R.id.spinnerHealthCentre)

    private val settingsButton: ImageView?
        get() = alertDialog.findViewById(R.id.ivSettings)

    private var selectedHealthFacility: HealthFacility? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        (activity?.application as MyApp).appComponent.inject(this)
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        dialogView = inflater.inflate(R.layout.referral_dialog, null)
        alertDialog = AlertDialog.Builder(requireActivity())
            .setView(dialogView)
            .setTitle("Send Referral")
            .setPositiveButton("Send SMS", null)
            .setNegativeButton("Send Web", null)
            .setNeutralButton("Cancel") { _, _ -> dialog?.cancel() }
            .create()

        // Override the default behaviour of the "Send SMS" and "Send Web"
        // buttons. Normally, they would close the dialog regardless of what
        // the listener does, but we don't want that. Instead we only want to
        // close the dialog once the referral has been successfully sent.
        alertDialog.setOnShowListener {
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                MainScope().launch {
                    sendReferralViaWeb()
                }
            }
        }

        alertDialog.show()

        // Setup UI components
        setupHealthFacilitySpinner()
        settingsButton?.setOnClickListener {
            val intent = SettingsActivity.makeLaunchIntent(requireActivity())
            activity?.startActivity(intent)
        }

        return alertDialog
    }

    /**
     * Pulls health facilities from the database and uses them to populate the
     * health facility spinner in this dialog.
     */
    private fun setupHealthFacilitySpinner() {
        MainScope().launch {
            val availableHealthFacilities = withContext(Dispatchers.IO) {
                healthCentreManager.getAllSelectedByUser()
            }
            val spinnerOptions = availableHealthFacilities.map(HealthFacility::name)
            val adapter = ArrayAdapter<String>(
                requireActivity(),
                android.R.layout.simple_dropdown_item_1line,
                spinnerOptions
            )
            healthFacilitySpinner?.adapter = adapter

            healthFacilitySpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedHealthFacility = null
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (healthFacilitySpinner?.selectedItemPosition?.let { it >= 0 } == true) {
                        selectedHealthFacility = availableHealthFacilities[position]
                    }
                }
            }
        }
    }

    /**
     * Constructs the patient and reading from the view models passed to this
     * fragment along with a new referral.
     *
     * @throws IllegalStateException if no health facility has been selected
     * @return the patient and reading constructed from the view models, the
     *  referral is nested within the resultant reading
     */
    private fun constructModels(): Pair<Patient, Reading> {
        val healthFacilityName = selectedHealthFacility?.name
            ?: throw IllegalStateException("no health facility selected")

        val (patient, reading) = viewModel.constructModels()
        reading.referral = Referral(
            commentBox?.text?.toString(),
            healthFacilityName,
            UnixTimestamp.now,
            patient.id,
            reading.id
        )

        return patient to reading
    }

    /**
     * Constructs and send the referral via HTTP.
     *
     * Closes this dialog upon successful upload. If the upload failed, then the
     * dialog is not closed as the user may wish to instead send the referral
     * via SMS.
     */
    private suspend fun sendReferralViaWeb() {
        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(alertDialog.context).apply {
            setMessage("Uploading Referral")
            setCancelable(false)
        }
        progressDialog.show()
        val (patient, reading) = constructModels()
        val result = referralUploadManger.uploadReferralViaWeb(patient, reading)
        progressDialog.cancel()
        when (result) {
            is Failure -> {
                val message = VolleyRequests.getServerErrorMessage(result.value)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            is Success -> {
                Toast.makeText(context, "Successfully uploaded referral", Toast.LENGTH_SHORT).show()

                // Save the patient and reading after marking them as being
                // uploaded. We save them here instead of delegating this task
                // to the calling activity because we need to process the models
                // before they are saved. If we were to leave it to the activity
                // it would need to reconstruct the models from the view model
                // instead of being able to used the result of the network call.
                patientManager.add(result.value.patient)
                readingManager.addReading(result.value.readings[0].apply { isUploadedToServer = true })
                dismiss()
                onSuccessfulUpload()
            }
        }
    }
}
