package com.cradleplatform.neptune.http_sms_service.sms.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SmsTransmissionDialogFragment : DialogFragment() {

    private val viewModel: SmsTransmissionDialogViewModel by viewModels()
    private lateinit var timer: CountDownTimer
    private lateinit var stateMessage: TextView
    private lateinit var sendProgressMessage: TextView
    private lateinit var receiveProgressMessage: TextView
    private lateinit var successFailMessage: TextView
    private lateinit var retryOrSyncMessage: TextView
    private lateinit var continueButton: Button
    private lateinit var cancelButton: Button
    private lateinit var retryButton: Button
    private lateinit var retryTimer: TextView

    companion object {
        const val TAG = "SmsTransmissionDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_sms_transmission_dialog,
            container,
            false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Access views from the layout
        stateMessage = view.findViewById<TextView>(R.id.stateMessage)
        sendProgressMessage = view.findViewById<TextView>(R.id.sendProgressMessage)
        receiveProgressMessage = view.findViewById<TextView>(R.id.receiveProgressMessage)
        successFailMessage = view.findViewById<TextView>(R.id.successErrorMessage)
        retryOrSyncMessage = view.findViewById<TextView>(R.id.retryMessage)
        continueButton = view.findViewById<Button>(R.id.btnPositive)
        cancelButton = view.findViewById<Button>(R.id.btnNegative)
        retryButton = view.findViewById<Button>(R.id.retry_fail)
        retryTimer = view.findViewById<TextView>(R.id.retryTimer)
        val countDownIntervalMilli: Long = 1000

        resetUI()

        viewModel.stateString.observe(viewLifecycleOwner) {
            stateMessage.text = it
        }
        viewModel.sendProgress.observe(viewLifecycleOwner) {
            sendProgressMessage.text = it
        }
        viewModel.receiveProgress.observe(viewLifecycleOwner) {
            receiveProgressMessage.text = it
        }
        viewModel.smsStateReporter.retry.observe(viewLifecycleOwner) {
            val smsStateReporter = viewModel.smsStateReporter
            if (it) {
                if (::timer.isInitialized) {
                    timer.cancel()
                }
                retryTimer.isVisible = true
                timer = object : CountDownTimer(
                    smsStateReporter.timeout * 1000 * (smsStateReporter.retriesAttempted + 1),
                    countDownIntervalMilli
                ) {
                    override fun onTick(timeRemaining: Long) {
                        val seconds = timeRemaining / 1000
                        val text = "Retry attempt: ${smsStateReporter.retriesAttempted}" +
                            ", retrying in $seconds"
                        retryTimer.text = text
                    }
                    override fun onFinish() {
                        retryTimer.isVisible = false
                    }
                }.start()
            } else {
                if (::timer.isInitialized) {
                    timer.cancel()
                }
            }
        }
        viewModel.smsStateReporter.state.observe(viewLifecycleOwner) { state ->
            if (state == SmsTransmissionStates.GETTING_READY_TO_SEND) {
                sendProgressMessage.isVisible = true
                receiveProgressMessage.isVisible = true
                retryButton.isVisible = false
            } else if (state == SmsTransmissionStates.DONE) {
                continueButton.isEnabled = true
            } else if (state == SmsTransmissionStates.TIME_OUT) {
                continueButton.isVisible = false
                retryButton.isVisible = true
                sendProgressMessage.text = "No response from SMS server"
                receiveProgressMessage.isVisible = false
            }
        }
        viewModel.smsStateReporter.errorCode.observe(viewLifecycleOwner) {
            // Display response code from server
            when (it) {
                425 -> {
                    successFailMessage.isVisible = true
                    successFailMessage.text = "Performing request number update. Re-sending transmission."
                }
                in 400..599 -> {
                    successFailMessage.isVisible = true
                    successFailMessage.text = "Error: ${viewModel.smsStateReporter.errorMsg.value}"
                    retryOrSyncMessage.isVisible = true
                    retryButton.isVisible = true
                    sendProgressMessage.isVisible = false
                    receiveProgressMessage.isVisible = false
                    continueButton.isVisible = false
                }
                200 -> {
                    successFailMessage.isVisible = true
                    successFailMessage.text = "Success: 200"
                }
                else -> {
                    successFailMessage.isVisible = false
                }
            }
        }
        // Set click listeners
        continueButton.setOnClickListener {
            dismiss()

            // TODO: We want manually exit Activity/Fragments so user can review the result
        }
        cancelButton.setOnClickListener {
            // TODO: kill/interrupt transmission, reverse DB modifications
            viewModel.smsSender.reset()
            viewModel.smsStateReporter.clearErrorCode()
            dismiss()
        }
        retryButton.setOnClickListener {
            viewModel.smsStateReporter.initRetransmission()
            viewModel.smsStateReporter.clearErrorCode()
            resetUI()
        }
    }

    private fun resetUI() {
        // Set initial values or customize views
        continueButton.isEnabled = false
        successFailMessage.isVisible = false
        retryButton.isVisible = false
        sendProgressMessage.isVisible = true
        receiveProgressMessage.isVisible = true
        retryOrSyncMessage.isVisible =false
    }

    override fun onDestroy() {
        Log.d(TAG, "$TAG::onDestroy()")
        super.onDestroy()
    }
}
