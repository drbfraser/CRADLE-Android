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
) {
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
        totalReceived.postValue(1)
        totalToBeReceived = numberOfSmsToReceive
    }

    fun incrementSent() {
        totalSent.postValue((totalSent.value ?: 0) + 1)
    }

    fun incrementReceived() {
        totalReceived.postValue((totalReceived.value ?: 0) + 1)
    }

    fun postException(code: Int) {
        errorCode.postValue(code)
        state.postValue(SmsTransmissionStates.EXCEPTION)
    }

    fun decryptMessage(encryptedMessage: String) {
        val secretKey = smsKeyManager.retrieveSmsKey()
        SMSFormatter.decodeMsg(encryptedMessage, secretKey)
            .let {
                decryptedMsg = it
//                val mappedJson = JSONObject(decryptedMsg)
                // TODO: Do something with the JSON object sent back. As for now, it is the same
                //  data that was sent out. Compare and make sure everything was correct?
                Log.d("SmsStateReporter", "Decrypted Message: $it")
                // if failed, post exception instead of
                // else it's DONE
                state.postValue(SmsTransmissionStates.DONE)
            }
    }
}
