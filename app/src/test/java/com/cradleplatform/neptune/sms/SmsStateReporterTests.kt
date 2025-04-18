package com.cradleplatform.neptune.sms

import android.content.SharedPreferences
import android.util.Log
import com.cradleplatform.neptune.http_sms_service.sms.SMSFormatter
import com.cradleplatform.neptune.http_sms_service.sms.SmsErrorHandler.InnerRequestError
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.DecryptedSmsResponse
import com.cradleplatform.neptune.model.SmsRelayErrorResponse425
import com.cradleplatform.neptune.utilities.extensions.InstantExecutorExtension
import com.google.gson.Gson
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

// Reference for annotation to handle liveData object posting:
// https://stackoverflow.com/questions/45988310/setvalue-and-postvalue-on-mutablelivedata-in-unittest
@ExtendWith(InstantExecutorExtension::class)
class SmsStateReporterTests {

    private val smsKeyManager = mockk<SmsKeyManager>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private lateinit var smsStateReporter: SmsStateReporter

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkObject(SMSFormatter)

        smsStateReporter = SmsStateReporter(smsKeyManager, sharedPreferences)
    }

    @AfterEach
    fun afterAll() {
        unmockkAll()
    }

    @Test
    fun `handleResponse should identify outer error and update error state`() {
        val relayData =
            "Unable to verify message from (+15555215556). Either the App and server don't agree on the security key or the message was corrupted."
        val badRequestErrorCode = 400

        smsStateReporter.handleResponse(relayData, badRequestErrorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(relayData, smsStateReporter.errorMsg.value)
        Assertions.assertEquals(relayData, smsStateReporter.errorMessageToCollect.value)
        Assertions.assertEquals(badRequestErrorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(badRequestErrorCode, smsStateReporter.statusCodeToCollect.value)

    }

    @Test
    fun `handleResponse should identify request number mismatch and start retransmission`() {
        val mockEncryptedResponse = "Mock encrypted error"
        val requestNumMismatchErrorCode = 425
        val errorMsg = "Mocked request number error"
        val expectedRequestNum = 0

        val innerBody = SmsRelayErrorResponse425(errorMsg, expectedRequestNum)

        val decryptedInnerResponseJson = Gson().toJson(
            DecryptedSmsResponse(requestNumMismatchErrorCode, Gson().toJson(innerBody))
        )

        every { SMSFormatter.decodeMsg(any(), any()) } returns decryptedInnerResponseJson

        smsStateReporter.handleResponse(mockEncryptedResponse, requestNumMismatchErrorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(requestNumMismatchErrorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(
            requestNumMismatchErrorCode,
            smsStateReporter.statusCodeToCollect.value
        )

    }

    @Test
    fun `handleResponse should update to error state after max request number mismatch retries`() {
        val mockEncryptedResponse = "Mock encrypted response"
        val requestNumMismatchErrorCode = 425
        val errorMsg = "Mocked request number error"
        val expectedRequestNum = 0
        val maxRetries = 2

        val innerBody = SmsRelayErrorResponse425(errorMsg, expectedRequestNum)

        val decryptedInnerResponseJson = Gson().toJson(
            DecryptedSmsResponse(requestNumMismatchErrorCode, Gson().toJson(innerBody))
        )

        every { SMSFormatter.decodeMsg(any(), any()) } returns decryptedInnerResponseJson

        repeat(maxRetries) { retry ->
            smsStateReporter.handleResponse(mockEncryptedResponse, requestNumMismatchErrorCode)

            Assertions.assertEquals(
                SmsTransmissionStates.RETRANSMISSION,
                smsStateReporter.state.value
            )
            Assertions.assertEquals(
                SmsTransmissionStates.RETRANSMISSION,
                smsStateReporter.stateToCollect.value
            )
            Assertions.assertEquals(requestNumMismatchErrorCode, smsStateReporter.statusCode.value)
            Assertions.assertEquals(
                requestNumMismatchErrorCode,
                smsStateReporter.statusCodeToCollect.value
            )
        }

        // Attempt #3
        smsStateReporter.handleResponse(mockEncryptedResponse, requestNumMismatchErrorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(errorMsg, smsStateReporter.errorMsg.value)
        Assertions.assertEquals(errorMsg, smsStateReporter.errorMessageToCollect.value)

        Assertions.assertEquals(requestNumMismatchErrorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(
            requestNumMismatchErrorCode,
            smsStateReporter.statusCodeToCollect.value
        )
    }


    @Test
    fun `handleResponse should detect inner error and update error states`() {
        val mockOuterResponse = "Mock successful outer response"
        val errorMsg = "Mocked inner request error"
        val badRequestErrorCode = 400

        val innerBody = InnerRequestError(errorMsg)
        val innerBodyJson = Gson().toJson(innerBody)

        val decryptedInnerResponse = DecryptedSmsResponse(badRequestErrorCode, innerBodyJson)
        val decryptedInnerResponseJson = Gson().toJson(decryptedInnerResponse)

        every { SMSFormatter.decodeMsg(any(), any()) } returns decryptedInnerResponseJson

        smsStateReporter.handleResponse(mockOuterResponse, null)

        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(errorMsg, smsStateReporter.errorMsg.value)
        Assertions.assertEquals(errorMsg, smsStateReporter.errorMessageToCollect.value)
        Assertions.assertEquals(badRequestErrorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(badRequestErrorCode, smsStateReporter.statusCodeToCollect.value)
    }

    @Test
    fun `handleResponse should handle successful response and update state`() {
        val successCode = 200
        val successMsg = "Success message"

        val decryptedInnerResponse = DecryptedSmsResponse(200, successMsg)
        val decryptedInnerResponseJson = Gson().toJson(decryptedInnerResponse)

        every { SMSFormatter.decodeMsg(any(), any()) } returns decryptedInnerResponseJson

        smsStateReporter.handleResponse("mockMsg", null)

        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.WAITING_FOR_USER_RESPONSE,
            smsStateReporter.stateToCollect.value
        )

        Assertions.assertEquals(successCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(successCode, smsStateReporter.statusCodeToCollect.value)
    }
}