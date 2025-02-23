package com.cradleplatform.neptune.http_sms_service.sms

import android.util.Log
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.SmsRelayErrorResponse
import com.google.gson.Gson
import javax.inject.Inject

class SmsErrorHandler @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
    private val smsStateReporter: SmsStateReporter
) {

    companion object {
        private const val REQUEST_NUMBER_MISMATCH = 425
    }

    fun shouldDecryptError(errCode: Int): Boolean {
        val encryptedErrorCodes = listOf(REQUEST_NUMBER_MISMATCH)
        return errCode in encryptedErrorCodes
    }

    fun handleEncryptedError(errCode: Int, encryptedMsg: String): String {
        val smsKey = smsKeyManager.retrieveSmsKey()!!
        val errorJsonString = SMSFormatter.decodeMsg(encryptedMsg, smsKey.key)
        val errorResponse = Gson().fromJson(errorJsonString, SmsRelayErrorResponse::class.java)
        Log.e("SmsStateReporter",
            "Handling Encrypted Error - Error Code: $errCode Decrypted Error Msg: ${errorResponse.message}")
        when (errCode) {
            REQUEST_NUMBER_MISMATCH -> handleRequestNumberMismatch(errorResponse)
        }
        return errorResponse.message
    }

    private fun handleRequestNumberMismatch(errorResponse: SmsRelayErrorResponse) {
        val expectedRequestNumber = errorResponse.expectedRequestNumber
        smsStateReporter.updateRequestNumber(expectedRequestNumber ?: 0)
        Log.d("SmsStateReporter", "Request Number Mismatch - Updating to $expectedRequestNumber")
    }
}
