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
    private var smsFormatter: SMSFormatter = SMSFormatter()

    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.GETTING_READY_TO_SEND)

    val totalSent = MutableLiveData<Int>(0)
    val totalReceived = MutableLiveData<Int>(0)
    val errorCode = MutableLiveData<Int>(0)
    val errorMsg = MutableLiveData<String>("")

    var totalToBeSent = 0
    var totalToBeReceived = 0

    private var sent = 0
    private var received = 0
    private var decryptedMsg = ""

    fun initSending(numberOfSmsToSend: Int) {
        state.postValue((SmsTransmissionStates.GETTING_READY_TO_SEND))
        sent = 0
        totalSent.postValue(0)
        totalToBeSent = numberOfSmsToSend
        received = 0
        totalReceived.postValue(0)
        totalToBeReceived = 0
        errorCode.postValue(0)
        errorMsg.postValue("")
    }

    fun initReceiving(numberOfSmsToReceive: Int) {
        state.postValue((SmsTransmissionStates.RECEIVING_SERVER_RESPONSE))
        totalReceived.postValue(1)
        totalToBeReceived = numberOfSmsToReceive
    }

    fun incrementSent() {
        totalSent.postValue(++sent)
    }

    fun incrementReceived() {
        totalReceived.postValue(++received)
    }

    fun postException(code: Int) {
        errorCode.postValue(code)
        state.postValue(SmsTransmissionStates.EXCEPTION)
    }

    fun handleResponse(msg: String, errCode: Int?) {
        if (errCode != null) {
            errorCode.postValue(errCode)
            errorMsg.postValue(msg)
            Log.d("SmsStateReporter", "Error Code: $errCode Error Msg: $msg")
        } else {
            val secretKey = smsKeyManager.retrieveSmsKey()
            SMSFormatter.decodeMsg(msg, secretKey)
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
}
