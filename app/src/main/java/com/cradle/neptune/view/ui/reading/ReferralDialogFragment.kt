package com.cradle.neptune.view.ui.reading

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.cradle.neptune.R
import com.cradle.neptune.binding.FragmentDataBindingComponent
import com.cradle.neptune.databinding.ReferralDialogBinding
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.view.ReadingActivity
import com.cradle.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.cradle.neptune.viewmodel.PatientReadingViewModel
import com.cradle.neptune.viewmodel.ReadingFlowSaveResult
import com.cradle.neptune.viewmodel.ReferralDialogViewModel
import com.cradle.neptune.viewmodel.ReferralOption
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * src: https://medium.com/alexander-schaefer/
 *      implementing-the-new-material-design-full-screen-dialog-for-android-e9dcc712cb38
 */
@AndroidEntryPoint
@Suppress("LargeClass")
class ReferralDialogFragment : DialogFragment() {
    private val viewModel: PatientReadingViewModel by activityViewModels()

    private var binding: ReferralDialogBinding? = null

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    /** ViewModel to store input */
    private val referralDialogViewModel: ReferralDialogViewModel by viewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(context is ReadingActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isCancelable = false
        val dataBinding = DataBindingUtil.inflate<ReferralDialogBinding>(
            inflater,
            R.layout.referral_dialog,
            container,
            false,
            dataBindingComponent
        )
        binding = dataBinding
        return dataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.viewModel = referralDialogViewModel
        binding?.lifecycleOwner = viewLifecycleOwner
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Toolbar>(R.id.referral_toolbar).apply {
            setNavigationOnClickListener { dismiss() }
            title = getString(R.string.referral_dialog_title)
        }

        view.findViewById<ImageButton>(R.id.open_health_centre_settings_button).setOnClickListener {
            activity?.apply { startActivity(HealthFacilitiesActivity.makeIntent(this)) }
        }

        view.findViewById<Button>(R.id.send_sms_button).setOnClickListener {
            if (referralDialogViewModel.isSelectedHealthCentreValid() &&
                    referralDialogViewModel.isSending.value != true) {
                referralDialogViewModel.isSending.value = true

                lifecycleScope.launch { handleSmsReferralSend(view) }
            }
        }

        view.findViewById<Button>(R.id.send_web_button).setOnClickListener {
            if (referralDialogViewModel.isSelectedHealthCentreValid() &&
                    referralDialogViewModel.isSending.value != true) {
                referralDialogViewModel.isSending.value = true

                lifecycleScope.launch { handleWebReferralSend(view) }
            }
        }
    }

    private suspend fun handleSmsReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthCentreName =
                referralDialogViewModel.healthCentreToUse.value ?: return

            val (smsSendResult, patientAndReadings) = viewModel.saveWithReferral(
                ReferralOption.SMS, comment, selectedHealthCentreName
            )

            when (smsSendResult) {
                ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                    launchSmsIntentAndFinish(
                        selectedHealthCentreName,
                        patientAndReadings ?: error("unreachable")
                    )
                }
                else -> {
                    Toast.makeText(
                        view.context,
                        "Error saving patient and reading",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } finally {
            referralDialogViewModel.isSending.value = false
        }
    }

    private fun launchSmsIntentAndFinish(
        selectedHealthCentreName: String,
        patientAndReadings: PatientAndReadings
    ) {
        activity?.run {
            val smsIntent = makeSmsIntent(selectedHealthCentreName, patientAndReadings)
            Toast.makeText(
                this,
                "Patient / reading has been saved successfully.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(smsIntent)
            finish()
        }
    }

    private fun makeSmsIntent(
        selectedHealthCentreName: String,
        patientAndReadings: PatientAndReadings
    ): Intent {
        val selectedHealthFacility =
            referralDialogViewModel.getHealthCentreFromHealthCentreName(
                selectedHealthCentreName
            )
        val id = UUID.randomUUID().toString()
        val json = with(JSONObject()) {
            put("referralId", id)
            put("patient", patientAndReadings.marshal())
        }
        val phoneNumber = selectedHealthFacility.phoneNumber
        val uri = Uri.parse("smsto:$phoneNumber")

        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("address", phoneNumber)
            putExtra("sms_body", json.toString())

            // Use default SMS app if supported
            // https://stackoverflow.com/a/24804601
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Telephony.Sms.getDefaultSmsPackage(context)?.let {
                    setPackage(it)
                }
            }
        }
    }

    private suspend fun handleWebReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthCentreName =
                referralDialogViewModel.healthCentreToUse.value ?: return

            val (smsSendResult, patientAndReadings) = viewModel.saveWithReferral(
                ReferralOption.WEB, comment, selectedHealthCentreName
            )

            when (smsSendResult) {
                ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                    // Nothing left for us to do.
                    Toast.makeText(
                        view.context,
                        "Patient / reading has been saved, and the referral has been sent.",
                        Toast.LENGTH_LONG
                    ).show()
                    activity?.finish()
                }
                ReadingFlowSaveResult.ERROR_UPLOADING_REFERRAL -> {
                    Toast.makeText(
                        view.context,
                        "Error uploading referral",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        view.context,
                        "Error saving patient and reading",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } finally {
            referralDialogViewModel.isSending.value = false
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            // Make the dialog close to full screen.
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setStyle(STYLE_NORMAL, R.style.AppTheme_FullScreenDialog)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.setInputEnabledState(true)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.setInputEnabledState(false)
        return super.onCreateDialog(savedInstanceState)
    }

    companion object {
        private const val TAG = "ReferralDialogFragment"
    }
}
