package com.cradleplatform.neptune.sms

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import com.cradleplatform.neptune.http_sms_service.sms.SMSFormatter
import com.cradleplatform.neptune.http_sms_service.sms.SmsErrorHandler
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.DecryptedSmsResponse
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

class SmsErrorHandlerTests {
    private val smsKeyManager = mockk<SmsKeyManager>(relaxed = true)
    private val smsStateReporter = mockk<SmsStateReporter>(relaxed = true)
    private lateinit var smsErrorHandler: SmsErrorHandler

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        smsErrorHandler = SmsErrorHandler(smsKeyManager, smsStateReporter)
    }

    @AfterEach
    fun afterAll() {
        unmockkAll()
    }

    @Test
    fun `handleOuterError should properly handle non-encrypted errors`() {
        val errorCode = 400
        val errorMsg = "Mock error message"

        val result = smsErrorHandler.handleOuterError(errorCode, errorMsg)

        Assertions.assertEquals(errorMsg, result)
    }

    @Test
    fun `handleOuterError should properly handle encrypted request number mismatch error`() {
        val errorCode = 425
        val encryptedErrorMsg = "Encrypted error message"
        val decryptedErrorMsg = "Decrypted error message"
        val expectedRequestNum = 0

        val errorResponseJson = """
            {
                "message": "$decryptedErrorMsg",
                "expectedRequestNumber": $expectedRequestNum
            }
        """

        val decryptedSmsResponseJson = """
            {
                "code": $errorCode,
                "body": ${Gson().toJson(errorResponseJson)}
            }
        """

        mockkObject(SMSFormatter)
        every { SMSFormatter.decodeMsg(any(), any()) } returns decryptedSmsResponseJson

        val result = smsErrorHandler.handleOuterError(errorCode, encryptedErrorMsg)

        Assertions.assertEquals(decryptedErrorMsg, result)
    }

    @Test
    fun `handleInnerError should return error message from inner error`() {
        val errorCode = 401
        val errorMsg = "Mock error message"
        val descriptionJson = """{ "description": "$errorMsg" }"""
        val decryptedResponse = DecryptedSmsResponse(errorCode, descriptionJson)

        val result = smsErrorHandler.handleInnerError(decryptedResponse)

        Assertions.assertEquals(errorMsg, result)
    }

    @Test
    fun `handleInnerError should return Unknown Error when description is missing`() {
        val errorCode = 401
        val emptyBodyDescriptionJson = "{}"
        val decryptedResponse = DecryptedSmsResponse(errorCode, emptyBodyDescriptionJson)

        val result = smsErrorHandler.handleInnerError(decryptedResponse)

        Assertions.assertEquals("Unknown Error", result)
    }


}