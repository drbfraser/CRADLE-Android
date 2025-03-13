package com.cradleplatform.neptune.http_sms_service.sms

import android.util.Log
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.SmsRelayErrorResponse425
import com.cradleplatform.neptune.model.DecryptedSmsResponse
import com.google.gson.Gson
import javax.inject.Inject

class SmsErrorHandler @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
    private val smsStateReporter: SmsStateReporter
) {
    companion object {
        const val REQUEST_NUMBER_MISMATCH = 425
        private const val TAG = "SmsErrorHandler"

        fun isErrorCode(code: Int): Boolean = code >= 400
    }

    private fun handleRequestNumberMismatch(errorResponse: SmsRelayErrorResponse425) {
        val expectedRequestNumber = errorResponse.expectedRequestNumber
        smsStateReporter.updateRequestNumber(expectedRequestNumber ?: 0)
        Log.d(TAG, "Request Number Mismatch - Updating to $expectedRequestNumber")
    }

    private fun shouldDecryptRelayError(errCode: Int): Boolean {
        val encryptedErrorCodes = listOf(REQUEST_NUMBER_MISMATCH)
        return errCode in encryptedErrorCodes
    }

    private fun handleEncryptedRelayError(errCode: Int, encryptedMsg: String): String {
        val smsKey = smsKeyManager.retrieveSmsKey()!!
        val decodedMsg = SMSFormatter.decodeMsg(encryptedMsg, smsKey.key)
        val decryptedSmsResponse = Gson().fromJson(decodedMsg, DecryptedSmsResponse::class.java)
        val errorResponse =
            Gson().fromJson(decryptedSmsResponse.body, SmsRelayErrorResponse425::class.java)

        Log.e(
            TAG,
            "Handling Encrypted Error - Error Code: $errCode Decrypted Error Msg: ${errorResponse.message}"
        )
        when (errCode) {
            REQUEST_NUMBER_MISMATCH -> handleRequestNumberMismatch(errorResponse)
        }
        return errorResponse.message
    }

    fun handleOuterError(
        outerErrorCode: Int,
        msg: String,
    ): String {
        var errorMsg = msg
        if (shouldDecryptRelayError(outerErrorCode)) {
            errorMsg = handleEncryptedRelayError(outerErrorCode, msg)
            Log.d(TAG, "Error Code: $outerErrorCode Decrypted Error Msg: $errorMsg")
        } else {
            Log.d(TAG, "Error Code: $outerErrorCode Error Msg: $msg")
        }


        return errorMsg
    }

    private data class InnerRequestError(
        val description: String?
    )

    fun handleInnerError(innerRequestResponse: DecryptedSmsResponse): String {
        val innerRequestError =
            Gson().fromJson(innerRequestResponse.body, InnerRequestError::class.java)
        val errorMsg = innerRequestError.description ?: "Unknown Error"

        Log.e(TAG, "Inner Error Code: ${innerRequestResponse.code}, Error Msg: $errorMsg")
        return errorMsg
    }
}
