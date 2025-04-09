package com.cradleplatform.neptune.sms

import android.content.SharedPreferences
import android.util.Log
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

        mockkConstructor(Gson::class)

        smsStateReporter = SmsStateReporter(smsKeyManager, sharedPreferences)
    }

    @AfterEach
    fun afterAll() {
        unmockkAll()
    }

    @Test
    fun `handleResponse should handle outer error and update error state`() {
        val relayData = "Unable to verify message from (+15555215556). Either the App and server don't agree on the security key or the message was corrupted."
        val errorCode = 401

        smsStateReporter.handleResponse(relayData, errorCode)

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
        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)

    }

    @Test
    fun `handleResponse should handle request number mismatch and start retransmission`() {
        val relayData = "Unable to verify message from (+15555215556). Either the App and server don't agree on the security key or the message was corrupted."
        val errorCode = 425
        val errorMsg = "Mocked request number error"
        val expectedRequestNum = 0

        every { anyConstructed<Gson>().fromJson(any<String>(), SmsRelayErrorResponse425::class.java) } returns SmsRelayErrorResponse425(errorMsg, expectedRequestNum)
        every { anyConstructed<Gson>().fromJson(any<String>(), DecryptedSmsResponse::class.java) } returns DecryptedSmsResponse(errorCode, errorMsg)

        smsStateReporter.handleResponse(relayData, errorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)

    }

    @Test
    fun `handleResponse should update to error state once max request number mismatch retries is reached`() {
        val mockResponse = "Mock Response"
        val errorCode = 425
        val errorMsg = "Mocked request number error"
        val expectedRequestNum = 0

        every { anyConstructed<Gson>().fromJson(any<String>(), SmsRelayErrorResponse425::class.java) } returns SmsRelayErrorResponse425(errorMsg, expectedRequestNum)
        every { anyConstructed<Gson>().fromJson(any<String>(), DecryptedSmsResponse::class.java) } returns DecryptedSmsResponse(errorCode, errorMsg)

        // Attempt # 1
        smsStateReporter.handleResponse(mockResponse, errorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)

        // Attempt #2
        smsStateReporter.handleResponse(mockResponse, errorCode)

        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.state.value
        )
        Assertions.assertEquals(
            SmsTransmissionStates.RETRANSMISSION,
            smsStateReporter.stateToCollect.value
        )
        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)

        // Attempt #3
        smsStateReporter.handleResponse(mockResponse, errorCode)

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

        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)
    }


    @Test
    fun `handleResponse should handle inner error and update error state`() {
        val decodedMsg = """{"code": 400, "msg": "Invalid data"}"""
        val errorMsg = "Mocked inner request error"
        val errorCode = 400

        every { anyConstructed<Gson>().fromJson(any<String>(), DecryptedSmsResponse::class.java) } returns DecryptedSmsResponse(errorCode, "Mocked error message")
        every { anyConstructed<Gson>().fromJson(any<String>(), InnerRequestError::class.java) } returns InnerRequestError(errorMsg)

        smsStateReporter.handleResponse(decodedMsg, null)

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
        Assertions.assertEquals(errorCode, smsStateReporter.statusCode.value)
        Assertions.assertEquals(errorCode, smsStateReporter.statusCodeToCollect.value)
    }

    @Test
    fun `handleResponse should handle successful response and update state`() {
        val successCode = 200

        every { anyConstructed<Gson>().fromJson(any<String>(), DecryptedSmsResponse::class.java) } returns DecryptedSmsResponse(successCode, "Mocked successful message")

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