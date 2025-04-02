package com.cradleplatform.neptune.sms

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import com.cradleplatform.neptune.manager.SmsKeyManager
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class SmsStateReporterTests {

    private val smsKeyManager = mockk<SmsKeyManager>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private lateinit var smsStateReporter: SmsStateReporter
//
    @BeforeEach
    fun setUp() {
        mockkStatic(Looper::class)
//        val looper = mockk<Looper> {
//            every { thread } returns Thread.currentThread()
//        }
        every { Looper.getMainLooper() } returns mockk {
            every { thread } returns Thread.currentThread()
        }

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers { firstArg<Runnable>().run(); true }
//        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers { firstArg<Runnable>().run(); true }

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        smsStateReporter = SmsStateReporter(smsKeyManager, sharedPreferences)
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
}