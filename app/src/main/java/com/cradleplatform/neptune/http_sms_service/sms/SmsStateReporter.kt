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
    private lateinit var smsSender: SMSSender
    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.GETTING_READY_TO_SEND)

    // For "ToCollect" variables, see: https://developer.android.com/reference/android/arch/lifecycle/MutableLiveData#postValue(T)
    // There is a possibility for the normal variables to be overwritten on the main thread before they are registered, so these
    // "ToCollect" variables preserve the values until the values are successfully listened to/collected and processed.
    val stateToCollect = MutableLiveData(SmsTransmissionStates.GETTING_READY_TO_SEND)
    private var timeoutThread: Thread? = null
    val totalSent = MutableLiveData<Int>(0)
    val totalReceived = MutableLiveData<Int>(0)
    val errorCode = MutableLiveData<Int>(0)
    val errorCodeToCollect = MutableLiveData(0)
    val errorMsg = MutableLiveData<String>("")
    val errorMessageToCollect = MutableLiveData("")
    val decryptedMsgLiveData = MutableLiveData("")

    val milliseconds = 1000
    var totalToBeSent = 0
    var totalToBeReceived = 0

    private var sent = 0
    private var received = 0
    private var decryptedMsg = ""
    //Adjust this variable for the number of seconds before initial retry
    var timeout: Long = 10
    //Adjust this variable for the number of retry attempts
    var retriesAttempted = 0
    var maxAttempts = 3

    val retry = MutableLiveData<Boolean>(false)

    fun initSending(numberOfSmsToSend: Int) {
        state.postValue((SmsTransmissionStates.GETTING_READY_TO_SEND))
        sent = 0
        totalSent.postValue(0)
        totalToBeSent = numberOfSmsToSend
        received = 0
        totalReceived.postValue(0)
        totalToBeReceived = 0
        retriesAttempted = 0
        smsSender.changeShowDialog(true)
        timeoutFunction(timeout, 0)
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
            errorCode.postValue(errCode!!)
            errorMsg.postValue(msg)
            errorCodeToCollect.postValue(errCode!!)
            errorMessageToCollect.postValue(msg)
            Log.d("SmsStateReporter", "Error Code: $errCode Error Msg: $msg")
            state.postValue(SmsTransmissionStates.EXCEPTION)
            stateToCollect.postValue(SmsTransmissionStates.EXCEPTION)
        } else {
            val secretKey = smsKeyManager.retrieveSmsKey()
            SMSFormatter.decodeMsg(msg, secretKey)
                .let {
                    decryptedMsg = it
                    decryptedMsgLiveData.postValue(it)
//                val mappedJson = JSONObject(decryptedMsg)
                    // TODO: Do something with the JSON object sent back. As for now, it is the same
                    //  data that was sent out. Compare and make sure everything was correct?
                    Log.d("SmsStateReporter", "Decrypted Message: $it")
                    // if failed, post exception instead of
                    // else it's DONE
                    state.postValue(SmsTransmissionStates.DONE)
                    stateToCollect.postValue(SmsTransmissionStates.DONE)
                }
        }
    }

    fun setSmsSender(sender: SMSSender) {
        this.smsSender = sender
    }

    private var lastSent = 0
    private fun retrySMSMessage() {
        retriesAttempted += 1
        if (retriesAttempted > 0) {
            smsSender.changeShowDialog(false)
        }
        if (lastSent == sent) {
            smsSender.sendSmsMessage(false)
        }

        if (retriesAttempted < maxAttempts && lastSent == sent) {
            if (retriesAttempted > 1) {
                retry.postValue(true)
            }
            timeoutFunction(timeout, retriesAttempted)
        }
        lastSent = sent
    }

    fun resetStateReporter() {
        state.postValue((SmsTransmissionStates.GETTING_READY_TO_SEND))
        sent = 0
        totalSent.postValue(0)
        received = 0
        totalReceived.postValue(0)
        totalToBeReceived = 0
        retriesAttempted = 0
        timeoutThread?.interrupt()
    }

    private fun timeoutFunction(seconds: Long, attemptNumber: Int) {
        timeoutThread?.interrupt()
        timeoutThread = Thread {
            try {
                retry.postValue(true)
                Thread.sleep(seconds * milliseconds * (attemptNumber + 1))
                if (state.value == SmsTransmissionStates.SENDING_TO_RELAY_SERVER || sent != totalToBeSent) {
                    if (attemptNumber < maxAttempts - 1) {
                        retrySMSMessage()
                    } else {
                        state.postValue(SmsTransmissionStates.TIME_OUT)
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            start()
        }
    }
}
