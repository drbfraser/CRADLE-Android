package com.cradleplatform.neptune.http_sms_service.sms.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Generate formatted String based on SmsStateReporter
 */
@HiltViewModel
class SmsTransmissionDialogViewModel @Inject constructor(
    private val smsStateReporter: SmsStateReporter,
) : ViewModel() {
    val stateString: LiveData<String> = smsStateReporter.state.map {
        when (it) {
            SmsTransmissionStates.GETTING_READY_TO_SEND -> "Queuing SMS to be sent..."
            SmsTransmissionStates.SENDING_TO_RELAY_SERVER -> "Sending..."
            SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE -> "Waiting for confirmation..."
            SmsTransmissionStates.RECEIVING_SERVER_RESPONSE -> "Receiving confirmation..."
            SmsTransmissionStates.DONE -> "Finished."
            SmsTransmissionStates.EXCEPTION -> "Something went wrong."
            else -> "Unknown state"
        }
    }
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
    val errorString: LiveData<String> = smsStateReporter.errorCode.map {
        if (it != 0) {
            "${smsStateReporter.errorCode.value} ${smsStateReporter.errorMsg.value}"
        } else {
            ""
        }
    }
}
