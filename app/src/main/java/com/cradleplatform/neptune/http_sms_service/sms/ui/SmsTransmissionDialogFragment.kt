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
class SmsTransmissionDialogFragment: DialogFragment() {

    private val viewModel: SmsTransmissionDialogViewModel by viewModels()
    private lateinit var timer: CountDownTimer

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
        val stateMessage = view.findViewById<TextView>(R.id.stateMessage)
        val sendProgressMessage = view.findViewById<TextView>(R.id.sendProgressMessage)
        val receiveProgressMessage = view.findViewById<TextView>(R.id.receiveProgressMessage)
        val successFailMessage = view.findViewById<TextView>(R.id.successErrorMessage)
        val positiveButton = view.findViewById<Button>(R.id.btnPositive)
        val negativeButton = view.findViewById<Button>(R.id.btnNegative)
        val retryButton = view.findViewById<Button>(R.id.retry_fail)
        val retryTimer = view.findViewById<TextView>(R.id.retryTimer)
        val countDownIntervalMilli: Long = 1000
        // Set initial values or customize views
        positiveButton.isEnabled = false
        retryButton.isVisible = false
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
            if (it) {
                if (::timer.isInitialized) {
                    timer.cancel()
                }
                retryTimer.isVisible = true
                timer = object :
                    CountDownTimer(viewModel.smsStateReporter.timeout * 1000 * (viewModel.smsStateReporter.retriesAttempted + 1),
                        countDownIntervalMilli) {
                    override fun onTick(timeRemaining: Long) {
                        val seconds = timeRemaining / 1000
                        retryTimer.text =
                            "Retry attempt: ${viewModel.smsStateReporter.retriesAttempted}, retrying in " + seconds.toString()
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
            if (state == SmsTransmissionStates.EXCEPTION || state == SmsTransmissionStates.DONE) {
                positiveButton.isEnabled = true
            } else if (state == SmsTransmissionStates.TIME_OUT) {
                positiveButton.isVisible = false
                retryButton.isVisible = true
                sendProgressMessage.text = "No response from SMS server"
                receiveProgressMessage.isVisible = false
            }
        }
        viewModel.smsStateReporter.errorCode.observe(viewLifecycleOwner) {
            // Display response code from server
            when (it) {
                // TODO: Currently no error code is in SMS received
                400 -> {
                    successFailMessage.visibility = View.VISIBLE
                    successFailMessage.text = "Failed: 400"
                }
                200 -> {
                    successFailMessage.visibility = View.VISIBLE
                    successFailMessage.text = "Success: 200"
                }
                else -> {
                    successFailMessage.visibility = View.GONE
                }
            }
        }
        // Set click listeners
        positiveButton.setOnClickListener {
            dismiss()

            // TODO: We want manually exit Activity/Fragments so user can review the result
        }
        negativeButton.setOnClickListener {
            // TODO: kill/interrupt transmission, reverse DB modifications
            viewModel.smsSender.reset()
            dismiss()
        }
        retryButton.setOnClickListener {
            viewModel.smsSender.reset()
            viewModel.smsStateReporter.resetStateReporter()
            viewModel.smsSender.queueRelayContent(viewModel.smsSender.data)
            receiveProgressMessage.isVisible = true
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "${TAG}::onDestroy()")
        super.onDestroy()
    }
}
