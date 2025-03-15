package com.cradleplatform.neptune.http_sms_service.sms.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Generate formatted String based on SmsStateReporter
 */
@HiltViewModel
class SmsTransmissionDialogViewModel @Inject constructor(
    val smsStateReporter: SmsStateReporter,
    val smsSender: SMSSender
) : ViewModel() {

    val stateString = MediatorLiveData<String>(). apply {
        addSource(smsStateReporter.state) { setStateString() }
        addSource(smsStateReporter.statusCode) { setStateString() }
    }

    private fun MediatorLiveData<String>.setStateString() {
        val state = smsStateReporter.state.value
        val statusCode = smsStateReporter.statusCode.value

        this.value = when (state) {
            SmsTransmissionStates.GETTING_READY_TO_SEND -> "Queuing SMS to be sent..."
            SmsTransmissionStates.SENDING_TO_RELAY_SERVER -> "Sending..."
            SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE -> "Waiting for confirmation..."
            SmsTransmissionStates.RECEIVING_SERVER_RESPONSE -> "Receiving confirmation..."
            SmsTransmissionStates.DONE -> "Processing, please wait..."
            SmsTransmissionStates.EXCEPTION -> "Handling error, exiting soon..."
            SmsTransmissionStates.TIME_OUT -> "Timed out, no response."
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE -> {
                when (statusCode) {
                    200 -> "Success! Please confirm."
                    in 400..599 -> "Error occurred, please review."
                    else -> "Please confirm."
                }
            }
            else -> "Unknown state"
        }
    }

//    val stateString: LiveData<String> = smsStateReporter.state.map {
//        when (it) {
//            SmsTransmissionStates.GETTING_READY_TO_SEND -> "Queuing SMS to be sent..."
//            SmsTransmissionStates.SENDING_TO_RELAY_SERVER -> "Sending..."
//            SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE -> "Waiting for confirmation..."
//            SmsTransmissionStates.RECEIVING_SERVER_RESPONSE -> "Receiving confirmation..."
//            SmsTransmissionStates.DONE -> "Processing, please wait..."
//            SmsTransmissionStates.EXCEPTION -> "Handling error, exiting soon..."
//            SmsTransmissionStates.TIME_OUT -> "Timed out, no response"
//            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE -> "Please confirm"
//            else -> "Unknown state"
//        }
//    }

    val sendProgress: LiveData<String> = smsStateReporter.totalSent.map {
        val numerator = it.toString()
        val denominator = smsStateReporter.totalToBeSent.toString()
        "Sending $numerator/$denominator"
    }
    val receiveProgress: LiveData<String> = smsStateReporter.totalReceived.map {
        val numerator = it.toString()
        val denominator = smsStateReporter.totalToBeReceived.toString()
        "Receiving $numerator/$denominator"
    }
    val errorString: LiveData<String> = smsStateReporter.statusCode.map {
        when (it) {
            // TODO: finish this
            404 -> "Failed"
            else -> ""
        }
    }
}
