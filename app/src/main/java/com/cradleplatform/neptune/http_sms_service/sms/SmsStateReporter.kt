package com.cradleplatform.neptune.http_sms_service.sms

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Singleton

/**
 * Instance for broadcasting current SMS sending/receiving progress.
 * Runs init() every time SMSSender calls send()
 */
@Singleton
class SmsStateReporter {
    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.GETTING_READY_TO_SEND)

    val totalSent = MutableLiveData<Int>(0)
    val totalReceived = MutableLiveData<Int>(0)

    var totalToBeSent = 0
    var totalToBeReceived = 0

    fun initSending(numberOfSmsToSend: Int) {
        state.postValue((SmsTransmissionStates.GETTING_READY_TO_SEND))
        totalSent.postValue(0)
        totalToBeSent = numberOfSmsToSend
    }

    fun initReceiving(numberOfSmsToReceive: Int) {
        state.postValue((SmsTransmissionStates.RECEIVING_SERVER_RESPONSE))
        totalReceived.postValue(0)
        totalToBeReceived = numberOfSmsToReceive
    }

    fun incrementSent() {
        totalSent.postValue((totalSent.value?: 0)+1)
    }

    fun incrementReceived() {
        totalReceived.postValue((totalReceived.value?: 0)+1)
    }
}