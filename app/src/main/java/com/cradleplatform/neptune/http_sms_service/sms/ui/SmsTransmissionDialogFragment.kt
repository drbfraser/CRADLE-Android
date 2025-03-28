package com.cradleplatform.neptune.http_sms_service.sms.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
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
    private var isRequestMismatch = false

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
            false
        )
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

        resetUI()
        setupObservers()
        setupClickListeners()
    }

    private fun resetUI() {
        // Set initial values or customize views
        continueButton.isVisible = false
        cancelButton.isVisible = true
        successFailMessage.isVisible = false
        retryButton.isVisible = false
        sendProgressMessage.isVisible = true
        receiveProgressMessage.isVisible = true
        retryOrSyncMessage.isVisible = false
        isRequestMismatch = false
        stateMessage.setTextColor(Color.BLACK)
        cancelButton.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.redDown))
        cancelButton.setTextColor(Color.WHITE)
    }

    private fun setupObservers() {
        viewModel.stateString.observe(viewLifecycleOwner) {
            stateMessage.text = it
        }
        viewModel.sendProgress.observe(viewLifecycleOwner) {
            sendProgressMessage.text = it
        }
        viewModel.receiveProgress.observe(viewLifecycleOwner) {
            receiveProgressMessage.text = it
        }
        viewModel.smsStateReporter.retry.observe(viewLifecycleOwner) { retry ->
            if (retry) startRetryTimer() else cancelRetryTimer()
        }
        viewModel.smsStateReporter.state.observe(viewLifecycleOwner) { state ->
            if (state == SmsTransmissionStates.TIME_OUT) {
                continueButton.isVisible = false
                retryButton.isVisible = true
                successFailMessage.text = "Error: No response from SMS server."
                retryOrSyncMessage.isVisible = true
                hideProgressMessages()
            }
        }
        viewModel.smsStateReporter.statusCode.observe(viewLifecycleOwner) { statusCode ->
            // Display response code from server
            if (statusCode != null) {
                handleStatusCodeUI(statusCode)
            }
        }
    }

    private fun setupClickListeners() {
        continueButton.setOnClickListener {
            viewModel.smsStateReporter.initDone()
            continueButton.isVisible = false
            // TODO: We want manually exit Activity/Fragments so user can review the result
        }
        cancelButton.setOnClickListener {
            // TODO: kill/interrupt transmission, reverse DB modifications
            if (viewModel.smsStateReporter.state.value == SmsTransmissionStates.WAITING_FOR_USER_RESPONSE) {
                viewModel.smsStateReporter.initException()
                cancelButton.isVisible = false
                retryButton.isVisible = false
            } else {
                dismiss()
            }

            viewModel.smsSender.reset()
        }
        retryButton.setOnClickListener {
            viewModel.smsStateReporter.initRetransmission()
            resetUI()
        }
    }

    private fun handleStatusCodeUI(statusCode: Int) {
        successFailMessage.isVisible = true
        if (statusCode == 425 &&
            viewModel.smsStateReporter.state.value != SmsTransmissionStates.WAITING_FOR_USER_RESPONSE
        ) {
            isRequestMismatch = true
            successFailMessage.text =
                "Performing request number update. Re-sending transmission."
        } else if (statusCode in 400..599 &&
            viewModel.smsStateReporter.state.value == SmsTransmissionStates.WAITING_FOR_USER_RESPONSE
        ) {
            hideProgressMessages()
            stateMessage.setTextColor(Color.RED)
            successFailMessage.text = "Error: ${viewModel.smsStateReporter.errorMsg.value}"
            retryOrSyncMessage.isVisible = true
            retryButton.isVisible = true
            continueButton.isVisible = false
            retryTimer.isVisible = false
            cancelButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            cancelButton.setTextColor(Color.RED)
            cancelButton.elevation = 0f
            cancelButton.stateListAnimator = null
        } else if (statusCode == 200) {
            hideProgressMessages()
            stateMessage.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.success_green
                )
            )
            successFailMessage.text = "Success! Data has been successfully transmitted."
            cancelButton.isVisible = false
            continueButton.isVisible = true
            isRequestMismatch = false
        } else {
            if (!isRequestMismatch) {
                successFailMessage.isVisible = false
            }
            retryOrSyncMessage.isVisible = false
        }
    }

    private fun hideProgressMessages() {
        sendProgressMessage.isVisible = false
        receiveProgressMessage.isVisible = false
    }

    private fun startRetryTimer() {
        val smsStateReporter = viewModel.smsStateReporter
        val countDownIntervalMilli: Long = 1000
        cancelRetryTimer()
        retryTimer.isVisible = true
        timer = object : CountDownTimer(
            smsStateReporter.timeout * 1000 * (smsStateReporter.retriesAttempted + 1),
            countDownIntervalMilli
        ) {
            override fun onTick(timeRemaining: Long) {
                val seconds = timeRemaining / 1000
                val text = "SMS Message Retry Attempt: ${smsStateReporter.retriesAttempted + 1}" +
                    ", retrying in $seconds"
                retryTimer.text = text
            }

            override fun onFinish() {
                retryTimer.isVisible = false
            }
        }.start()
    }

    private fun cancelRetryTimer() {
        if (::timer.isInitialized) {
            timer.cancel()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "$TAG::onDestroy()")
        super.onDestroy()
    }
}
