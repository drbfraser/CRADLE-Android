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
        private const val TAG = "SmsErrorHandler"
    }


    private fun handleRequestNumberMismatch(errorResponse: SmsRelayErrorResponse) {
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
        val errorJsonString = SMSFormatter.decodeMsg(encryptedMsg, smsKey.key)
        val errorResponse = Gson().fromJson(errorJsonString, SmsRelayErrorResponse::class.java)

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


    private data class InnerRequestData(
        val code: Int?,
        val description: String?
    )

    fun handleInnerError(innerErrorCode: Int, decryptedMsg: String): String {
        val innerRequestData = Gson().fromJson(decryptedMsg, InnerRequestData::class.java)
        val errorMsg = innerRequestData.description ?: "Unknown Error"

        Log.e(TAG, "Inner Error Code: $innerErrorCode, Error Msg: $errorMsg")
        return errorMsg
    }

    fun getInnerErrorCode(decryptedMsg: String): Int? {
        val innerRequestError = Gson().fromJson(decryptedMsg, InnerRequestData::class.java)
        val innerStatusCode = innerRequestError.code
        return if (innerStatusCode.toString().first() == '4') innerStatusCode else null
    }
}
