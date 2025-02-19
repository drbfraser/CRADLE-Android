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

        fun shouldDecryptError(errCode:Int): Boolean {
            val encryptedErrorCodes = listOf(409)
            return errCode in encryptedErrorCodes
        }

        fun handleEncryptedError(errCode:Int, encryptedMsg:String): String {
            val smsKey = smsKeyManager.retrieveSmsKey()!!
            SMSFormatter.decodeMsg(encryptedMsg, smsKey.key)
                .let {
                    val errorJsonString = it
                    val errorResponse = Gson().fromJson(errorJsonString, SmsRelayErrorResponse::class.java)
                    Log.d("SmsStateReporter", "Handling Encrypted Error - Error Code: $errCode Decrypted Error Msg: ${errorResponse.message}")
                    when(errCode) {
                        409 -> handleRequestNumberMismatch(errorResponse)
                    }
                    return errorResponse.message
                }
        }

        private fun handleRequestNumberMismatch(errorResponse: SmsRelayErrorResponse) {
            Log.d("LCDEBUG", "HANDLING REQUEST NO MISMATCH")
            val expectedRequestNumber = errorResponse.expectedRequestNumber
            smsStateReporter.requestNumber.postValue(expectedRequestNumber)
        }
}