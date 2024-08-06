package com.cradleplatform.neptune.view.ui.reading

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ReferralDialogBinding
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.PatientReadingViewModel
import com.cradleplatform.neptune.viewmodel.ReadingFlowSaveResult
import com.cradleplatform.neptune.viewmodel.ReferralDialogViewModel
import com.cradleplatform.neptune.viewmodel.ReferralOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    lateinit var dataPasser: OnReadingSendWebSnackbarMsgPass

    @Inject
    lateinit var smsKeyManager: SmsKeyManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var smsSender: SMSSender

    @Inject
    lateinit var smsDataProcessor: SMSDataProcessor
    override fun onAttach(context: Context) {
        super.onAttach(context)
        check(context is ReadingActivity)
        check(arguments != null)
        check(
            arguments?.getSerializable(ARG_LAUNCH_REASON) as? ReadingActivity.LaunchReason != null
        )

        dataPasser = context
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
    ): View {
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
        binding?.readingViewModel = viewModel
        binding?.launchReason = launchReason
        binding?.lifecycleOwner = viewLifecycleOwner
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Toolbar>(R.id.referral_toolbar).apply {
            setNavigationOnClickListener { dismiss() }
            title = getString(R.string.referral_dialog_title)
        }

        val sendWebButton = view.findViewById<Button>(R.id.send_web_button)
        sendWebButton.setOnClickListener {
            if (referralDialogViewModel.isSelectedHealthFacilityValid() &&
                referralDialogViewModel.isSending.value != true
            ) {
                referralDialogViewModel.isSending.value = true
                lifecycleScope.launch { handleWebReferralSend(view) }
            }
        }

        fun sendViaSMS () {
            if (referralDialogViewModel.isSelectedHealthFacilityValid() &&
                referralDialogViewModel.isSending.value != true
            ) {
                // Retrieve and validate the locally stored smsKey - allow user to send SMS if the smsKey is valid
                val currentSmsKey = smsKeyManager.retrieveSmsKey()
                val keyStatus: SmsKeyManager.KeyState = smsKeyManager.validateSmsKey(currentSmsKey)
                if (keyStatus == SmsKeyManager.KeyState.NORMAL || keyStatus == SmsKeyManager.KeyState.WARN) {
                    // SmsKey is normal or stale ==> Send SMS
                    referralDialogViewModel.isSending.value = true
                    lifecycleScope.launch { handleSmsReferralSend(view) }
                } else {
                    // SmsKey is invalid or expired ==> cannot send SMS
                    // Display a TOAST message to inform the user about the invalid or expired SMS key
                    val toastMessage = "Your SMS key has expired\n" +
                            "Unable to send SMS. Ensure internet connectivity and refresh your SMS key in the settings."
                    Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        view.findViewById<Button>(R.id.send_sms_button).setOnClickListener {
            val isTest: Boolean = true
            if (isTest) {
                showBetterConnectivityDialog(sendWebButton) { continueWithSMS ->
                    if (continueWithSMS) {
                        sendViaSMS()
                    }
                }
            } else {
                sendViaSMS()
            }
        }

        if (viewModel.isInitialized.value != true) {
            dismiss()
        }
    }

    private fun showBetterConnectivityDialog(sendWebButton: Button, onDialogClosed: (Boolean) -> Unit) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
        builder
            .setMessage("It seems that your current internet connection is stronger than the SMS network.\n" + "\n" +
                    "Would you like to send the request via data instead to ensure faster and more reliable delivery?")
            .setTitle("Better Connectivity Available")
            .setPositiveButton("CONTINUE WITH SMS") { dialog, which ->
                onDialogClosed(true)
            }
            .setNegativeButton("SEND VIA DATA") { dialog, which ->
                sendWebButton.performClick()
                onDialogClosed(false)
            }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private suspend fun handleSmsReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthFacilityName =
                referralDialogViewModel.healthFacilityToUse.value ?: return

            // Different from handleWebReferralSend(), the ViewModel does not send.
            // Instead, a local save happens and then a ReadingFlowSaveResult indicates
            // that an SMS send should follow
            val roomDbSaveResult = viewModel.saveWithReferral(
                ReferralOption.SMS,
                comment,
                selectedHealthFacilityName
            )
            when (roomDbSaveResult) {
                is ReadingFlowSaveResult.SaveSuccessful.ReferralSmsNeeded -> {
                    showStatusToast(view.context, roomDbSaveResult, ReferralOption.SMS)
                    val json = smsDataProcessor.processPatientAndReadingsToJSON(
                        roomDbSaveResult.patientInfoForReferral)
                    smsSender.queueRelayContent(json).let { enqueuSuccessful ->
                        if (enqueuSuccessful) {
                            smsSender.sendSmsMessage(false)
                        }
                    }
                    // TODO: Remove the following code from the UI and move to a more appropriate place
                    // This should not be in the UI and should be moved to a place where the server
                    // response is being parsed
                    roomDbSaveResult.patientInfoForReferral.patient.lastServerUpdate =
                        roomDbSaveResult.patientInfoForReferral.patient.lastEdited
                    viewModel.patientSentViaSMS(roomDbSaveResult.patientInfoForReferral.patient)
                }
                else -> {
                    showStatusToast(view.context, roomDbSaveResult, ReferralOption.SMS)
                }
            }
        } finally {
            referralDialogViewModel.isSending.value = false
        }
    }

    private suspend fun handleWebReferralSend(view: View) {
        try {
            val comment = referralDialogViewModel.comments.value ?: ""
            val selectedHealthFacilityName =
                referralDialogViewModel.healthFacilityToUse.value ?: return
            val webSendResult = viewModel.saveWithReferral(
                ReferralOption.HTTP,
                comment,
                selectedHealthFacilityName
            )

            if (webSendResult is ReadingFlowSaveResult.SaveSuccessful) {
                // Nothing left for us to do.
                dataPasser.onMsgPass(getToastMessageForStatus(view.context, webSendResult, ReferralOption.HTTP))
                activity?.finish()
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
        ReferralOption.HTTP -> when (result) {
            is ReadingFlowSaveResult.SaveSuccessful -> {
                check(result is ReadingFlowSaveResult.SaveSuccessful.NoSmsNeeded)
                if (launchReason == ReadingActivity.LaunchReason.LAUNCH_REASON_NEW) {
                    context.getString(R.string.dialog_referral_toast_web_success_new_patient)
                } else {
                    context.getString(R.string.dialog_referral_toast_web_success_new_reading_only)
                }
            }
            ReadingFlowSaveResult.ErrorUploadingReferral -> {
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
            is ReadingFlowSaveResult.SaveSuccessful -> {
                check(result is ReadingFlowSaveResult.SaveSuccessful.ReferralSmsNeeded)
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
        private const val ARG_LAUNCH_REASON = "LAUNCH_REASON"

        fun makeInstance(launchReason: ReadingActivity.LaunchReason) =
            ReferralDialogFragment().apply {
                arguments = Bundle().apply {
                    // Have it pass the launch reason so that we don't run into lateinit problems
                    putSerializable(ARG_LAUNCH_REASON, launchReason)
                }
            }
    }

    interface OnReadingSendWebSnackbarMsgPass {
        fun onMsgPass(data: String)
        fun sendSmsMessage(data: String)
    }
}
