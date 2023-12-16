package com.cradleplatform.neptune.http_sms_service.sms

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.utilities.SMSFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instance for broadcasting current SMS sending/receiving progress.
 * Runs init() every time SMSSender calls send()
 */
@Singleton
class SmsStateReporter @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
){
    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.GETTING_READY_TO_SEND)

    val totalSent = MutableLiveData<Int>(0)
    val totalReceived = MutableLiveData<Int>(0)
    val errorCode = MutableLiveData<Int>(0)

    var totalToBeSent = 0
    var totalToBeReceived = 0

    private var decryptedMsg = ""

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

    fun postException(code: Int) {
        errorCode.postValue(code)
    }

    fun decryptMessage(encryptedMessage: String) {
        val secretKey = smsKeyManager.retrieveSmsKey()
        SMSFormatter.decodeMsg(encryptedMessage, secretKey)
            .let {
            decryptedMsg = it
            Log.d("PETER_FAN", "Decrypted Message: $it")
            // if failed, post exception instead of
            // else
            state.postValue(SmsTransmissionStates.DONE)
        }
    }
}
