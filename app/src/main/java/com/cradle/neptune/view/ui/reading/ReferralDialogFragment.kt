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
import com.android.volley.TimeoutError
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.ReferralUploadManger
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Referral
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.Success
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
        fun makeInstance(viewModel: PatientReadingViewModel, callback: (String) -> Unit): ReferralDialogFragment {
            return ReferralDialogFragment(viewModel)
        }
    }

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
        alertDialog = AlertDialog.Builder(activity!!)
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
            val intent = SettingsActivity.makeLaunchIntent(activity!!)
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
            val adapter = ArrayAdapter<String>(activity!!, android.R.layout.simple_dropdown_item_1line, spinnerOptions)
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
        if (selectedHealthFacility == null) {
            throw IllegalStateException("no health facility selected")
        }

        val currentTime = UnixTimestamp.now
        val referral = Referral(currentTime, selectedHealthFacility!!.name, commentBox?.text.toString())
        val (patient, reading) = viewModel.constructModels()
        reading.referral = referral
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
                val err = result.value
                val message = when {
                    err.message != null -> err.message
                    err.networkResponse != null -> "HTTP: ${err.networkResponse.statusCode}"
                    err is TimeoutError -> "timeout"
                    else -> err.toString()
                }
                Toast.makeText(context, "Failed to upload referral: $message", Toast.LENGTH_LONG).show()
            }
            is Success -> {
                Toast.makeText(context, "Successfully uploaded referral", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }
}
