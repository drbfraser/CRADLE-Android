package com.cradleVSA.neptune.view.ui.reading

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
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
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.binding.FragmentDataBindingComponent
import com.cradleVSA.neptune.databinding.ReferralDialogBinding
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.view.ReadingActivity
import com.cradleVSA.neptune.view.ui.settings.ui.healthFacility.HealthFacilitiesActivity
import com.cradleVSA.neptune.viewmodel.PatientReadingViewModel
import com.cradleVSA.neptune.viewmodel.ReadingFlowSaveResult
import com.cradleVSA.neptune.viewmodel.ReferralDialogViewModel
import com.cradleVSA.neptune.viewmodel.ReferralOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

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

    private lateinit var launchReason: ReadingActivity.LaunchReason

    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(context is ReadingActivity)
        check(arguments != null)
        check(
            arguments?.getSerializable(ARG_LAUNCH_REASON) as? ReadingActivity.LaunchReason != null
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true

        launchReason = arguments?.getSerializable(ARG_LAUNCH_REASON)
            as? ReadingActivity.LaunchReason ?: error("unreachable")
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
        binding?.launchReason = launchReason
        binding?.lifecycleOwner = viewLifecycleOwner
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Toolbar>(R.id.referral_toolbar).apply {
            setNavigationOnClickListener { dismiss() }
            title = getString(R.string.referral_dialog_title)
        }

        view.findViewById<ImageButton>(R.id.open_health_facility_settings_button)
            .setOnClickListener {
                activity?.apply { startActivity(HealthFacilitiesActivity.makeIntent(this)) }
            }

        view.findViewById<Button>(R.id.send_sms_button).setOnClickListener {
            if (referralDialogViewModel.isSelectedHealthFacilityValid() &&
                referralDialogViewModel.isSending.value != true
            ) {
                referralDialogViewModel.isSending.value = true

                lifecycleScope.launch { handleSmsReferralSend(view) }
            }
        }

        view.findViewById<Button>(R.id.send_web_button).setOnClickListener {
            if (referralDialogViewModel.isSelectedHealthFacilityValid() &&
                referralDialogViewModel.isSending.value != true
            ) {
                referralDialogViewModel.isSending.value = true

                lifecycleScope.launch { handleWebReferralSend(view) }
            }
        }

        if (viewModel.isInitialized.value != true) {
            dismiss()
        }
    }

    private suspend fun handleSmsReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthFacilityName =
                referralDialogViewModel.healthFacilityToUse.value ?: return

            val (smsSendResult, patientAndReadings) = viewModel.saveWithReferral(
                ReferralOption.SMS,
                comment,
                selectedHealthFacilityName
            )

            when (smsSendResult) {
                ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                    showStatusToast(view.context, smsSendResult, ReferralOption.SMS)
                    launchSmsIntentAndFinish(
                        selectedHealthFacilityName,
                        patientAndReadings ?: error("unreachable")
                    )
                }
                else -> {
                    showStatusToast(view.context, smsSendResult, ReferralOption.SMS)
                }
            }
        } finally {
            referralDialogViewModel.isSending.value = false
        }
    }

    private fun launchSmsIntentAndFinish(
        selectedHealthFacilityName: String,
        patientAndReadings: PatientAndReadings
    ) {
        activity?.run {
            val smsIntent = makeSmsIntent(selectedHealthFacilityName, patientAndReadings)
            startActivity(smsIntent)
            finish()
        }
    }

    private fun makeSmsIntent(
        selectedHealthFacilityName: String,
        patientAndReadings: PatientAndReadings
    ): Intent {
        val selectedHealthFacility =
            referralDialogViewModel.getHealthFacilityFromHealthFacilityName(
                selectedHealthFacilityName
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
            Telephony.Sms.getDefaultSmsPackage(context)?.let {
                setPackage(it)
            }
        }
    }

    private suspend fun handleWebReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthFacilityName =
                referralDialogViewModel.healthFacilityToUse.value ?: return

            val (smsSendResult, _) = viewModel.saveWithReferral(
                ReferralOption.WEB,
                comment,
                selectedHealthFacilityName
            )

            when (smsSendResult) {
                ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                    // Nothing left for us to do.
                    showStatusToast(view.context, smsSendResult, ReferralOption.WEB)
                    activity?.finish()
                }
                ReadingFlowSaveResult.ERROR_UPLOADING_REFERRAL -> {
                    showStatusToast(view.context, smsSendResult, ReferralOption.WEB)
                }
                else -> {
                    showStatusToast(view.context, smsSendResult, ReferralOption.WEB)
                }
            }
        } finally {
            referralDialogViewModel.isSending.value = false
        }
    }

    private fun showStatusToast(
        context: Context,
        result: ReadingFlowSaveResult,
        referralOption: ReferralOption
    ) {
        Toast.makeText(
            context,
            getToastMessageForStatus(context, result, referralOption),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getToastMessageForStatus(
        context: Context,
        result: ReadingFlowSaveResult,
        referralOption: ReferralOption
    ) = when (referralOption) {
        ReferralOption.WEB -> when (result) {
            ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(R.string.dialog_referral_toast_web_success_new_patient)
                } else {
                    context.getString(R.string.dialog_referral_toast_web_success_new_reading_only)
                }
            }
            ReadingFlowSaveResult.ERROR_UPLOADING_REFERRAL -> {
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(
                        R.string.dialog_referral_toast_error_uploading_referral_reading_and_patient
                    )
                } else {
                    context.getString(
                        R.string.dialog_referral_toast_error_uploading_referral_reading
                    )
                }
            }
            else -> {
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(R.string.dialog_referral_toast_error_saving_patient_reading)
                } else {
                    context.getString(R.string.dialog_referral_toast_error_saving_reading)
                }
            }
        }
        ReferralOption.SMS -> when (result) {
            ReadingFlowSaveResult.SAVE_SUCCESSFUL -> {
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(R.string.dialog_referral_toast_sms_success_new_patient)
                } else {
                    context.getString(R.string.dialog_referral_toast_sms_success_new_reading_only)
                }
            }
            else -> {
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(R.string.dialog_referral_toast_error_saving_patient_reading)
                } else {
                    context.getString(R.string.dialog_referral_toast_error_saving_reading)
                }
            }
        }
        ReferralOption.NONE -> error("unreachable")
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            // Make the dialog close to full screen.
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
        private const val ARG_LAUNCH_REASON = "LAUNCH_REASON"

        fun makeInstance(launchReason: ReadingActivity.LaunchReason) =
            ReferralDialogFragment().apply {
                arguments = Bundle().apply {
                    // Have it pass the launch reason so that we don't run into lateinit problems
                    putSerializable(ARG_LAUNCH_REASON, launchReason)
                }
            }
    }
}
