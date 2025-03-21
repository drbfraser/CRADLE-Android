package com.cradleplatform.neptune.http_sms_service.sms

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.DecryptedSmsResponse
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Instance for broadcasting current SMS sending/receiving progress.
 * Runs init() every time SMSSender calls send()
 */
@Singleton
class SmsStateReporter @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
    private val encryptedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "SmsStateReporter"
        private const val MAX_REQUEST_NUMBER = 999999
        const val SMS_REQUEST_NUMBER_KEY = "requestNumber"
        private const val MAX_REQUEST_NUM_RETRIES = 2
    }

    private lateinit var smsSender: SMSSender
    private val smsErrorHandler = SmsErrorHandler(smsKeyManager, this)
    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.GETTING_READY_TO_SEND)

    // For "ToCollect" variables, see:
    // https://developer.android.com/reference/android/arch/lifecycle/MutableLiveData#postValue(T)
    // There is a possibility for the normal variables to be overwritten on the main thread before
    // they are registered, so these "ToCollect" variables preserve the values until the values are
    // successfully listened to/collected and processed.
    val stateToCollect = MutableLiveData(SmsTransmissionStates.GETTING_READY_TO_SEND)
    private var timeoutThread: Thread? = null
    val totalSent = MutableLiveData<Int>(0)
    val totalReceived = MutableLiveData<Int>(0)
    val statusCode = MutableLiveData<Int>(0)
    val statusCodeToCollect = MutableLiveData(0)
    val errorMsg = MutableLiveData("")
    val errorMessageToCollect = MutableLiveData("")
    val decryptedMsgLiveData = MutableLiveData("")

    private val milliseconds = 1000
    var totalToBeSent = 0
    var totalToBeReceived = 0

    private var sent = 0
    private var received = 0
    private var decryptedMsg = ""

    // Adjust this variable for the number of seconds before initial retry
    var timeout: Long = 10

    // Adjust this variable for the number of retry attempts
    var retriesAttempted = 0
    private var maxAttempts = 3

    private var requestNumberRetries = 0

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
        clearStatusCode()
    }

    fun initReceiving(numberOfSmsToReceive: Int) {
        state.postValue((SmsTransmissionStates.RECEIVING_SERVER_RESPONSE))
        totalReceived.postValue(1)
        totalToBeReceived = numberOfSmsToReceive
    }

    fun initRetransmission() {
        state.postValue(SmsTransmissionStates.RETRANSMISSION)
        stateToCollect.postValue(SmsTransmissionStates.RETRANSMISSION)
    }

    fun initException() {
        state.postValue(SmsTransmissionStates.EXCEPTION)
        stateToCollect.postValue(SmsTransmissionStates.EXCEPTION)
        resetRequestNumberRetries()
        clearStatusCode()
    }

    fun initDone() {
        state.postValue(SmsTransmissionStates.DONE)
        stateToCollect.postValue(SmsTransmissionStates.DONE)
        resetRequestNumberRetries()
        clearStatusCode()
    }

    fun clearStatusCode() {
        statusCode.postValue(0)
        statusCodeToCollect.postValue(0)
    }

    fun incrementSent() {
        totalSent.postValue(++sent)
    }

    fun incrementReceived() {
        totalReceived.postValue(++received)
    }

    fun getCurrentRequestNumber(): Int {
        return encryptedPreferences.getInt(SMS_REQUEST_NUMBER_KEY, 0)
    }

    fun updateRequestNumber(newRequestNumber: Int) {
        encryptedPreferences.edit().putInt(SMS_REQUEST_NUMBER_KEY, newRequestNumber).apply()
    }

    fun incrementRequestNumber() {
        val currentRequestNumber = getCurrentRequestNumber()
        val newRequestNumber = (currentRequestNumber + 1) % MAX_REQUEST_NUMBER
        updateRequestNumber(newRequestNumber)
    }

    private fun resetRequestNumberRetries() {
        requestNumberRetries = 0
    }

    private fun setSuccessStates(code: Int) {
        statusCode.postValue(code)
        statusCodeToCollect.postValue(code)

        state.postValue(SmsTransmissionStates.WAITING_FOR_USER_RESPONSE)
        stateToCollect.postValue(SmsTransmissionStates.WAITING_FOR_USER_RESPONSE)
    }

    private fun setErrorStates(code: Int, msg: String) {
        errorMsg.postValue(msg)
        errorMessageToCollect.postValue(msg)

        statusCode.postValue(code)
        statusCodeToCollect.postValue(code)

        state.postValue(SmsTransmissionStates.WAITING_FOR_USER_RESPONSE)
        stateToCollect.postValue(SmsTransmissionStates.WAITING_FOR_USER_RESPONSE)
    }

    fun handleResponse(msg: String, outerErrorCode: Int?) {
        if (outerErrorCode != null) {
            val errorMsg = smsErrorHandler.handleOuterError(outerErrorCode, msg)

            if (outerErrorCode == SmsErrorHandler.REQUEST_NUMBER_MISMATCH
                && requestNumberRetries < MAX_REQUEST_NUM_RETRIES
            ) {
                statusCode.postValue(SmsErrorHandler.REQUEST_NUMBER_MISMATCH)
                statusCodeToCollect.postValue(SmsErrorHandler.REQUEST_NUMBER_MISMATCH)
                initRetransmission()
                requestNumberRetries++
            } else {
                setErrorStates(outerErrorCode, errorMsg)
            }
        } else {
            val smsKey = smsKeyManager.retrieveSmsKey()!!
            val decodedMessage = SMSFormatter.decodeMsg(msg, smsKey.key)

            val innerRequestResponse =
                Gson().fromJson(decodedMessage, DecryptedSmsResponse::class.java)
            if (SmsErrorHandler.isErrorCode(innerRequestResponse.code)) {
                val errorMsg = smsErrorHandler.handleInnerError(innerRequestResponse)
                setErrorStates(innerRequestResponse.code, errorMsg)
                return
            }

            decryptedMsg = decodedMessage
            decryptedMsgLiveData.postValue(decodedMessage)
            // TODO: Do something with the JSON object sent back. As for now, it is the same
            //  data that was sent out. Compare and make sure everything was correct?
            // val mappedJson = JSONObject(decryptedMsg)
            Log.d(TAG, "Decrypted Message: $decodedMessage")

            setSuccessStates(200)
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
