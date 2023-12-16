package com.cradleplatform.neptune.http_sms_service.sms.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsTransmissionDialogFragment @Inject constructor(
    private val smsStateReporter: SmsStateReporter,
): DialogFragment() {

    private val viewModel = SmsTransmissionDialogViewModel(smsStateReporter)

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
        // Set initial values or customize views
        positiveButton.isEnabled = false
        viewModel.stateString.observe(viewLifecycleOwner) {
            stateMessage.text = it
        }
        viewModel.sendProgress.observe(viewLifecycleOwner) {
            sendProgressMessage.text = it
        }
        viewModel.receiveProgress.observe(viewLifecycleOwner) {
            receiveProgressMessage.text = it
        }
        smsStateReporter.state.observe(viewLifecycleOwner) { state ->
            if (state == SmsTransmissionStates.EXCEPTION || state == SmsTransmissionStates.DONE) {
                positiveButton.isEnabled = true
            }
        }
        smsStateReporter.errorCode.observe(viewLifecycleOwner) {
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
            // TODO: We want manually exit Acitivty/Fragments so user can review the result
        }
        negativeButton.setOnClickListener {
            // TODO: kill/interrupt transmission, reverse DB modifications
            dismiss()
        }
    }



}
