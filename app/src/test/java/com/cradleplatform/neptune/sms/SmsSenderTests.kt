package com.cradleplatform.neptune.sms

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.cradleplatform.neptune.http_sms_service.sms.SMSFormatter
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import com.cradleplatform.neptune.manager.SmsKeyManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsSenderTests {
    private val smsKeyManager = mockk<SmsKeyManager>(relaxed = true)
    private val sharedPreferences = mockk<SharedPreferences>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val smsStateReporter = mockk<SmsStateReporter>(relaxed = true)
    private val smsManager = mockk<SmsManager>(relaxed = true)
    private lateinit var smsSender: SMSSender
    private lateinit var smsRelayQueue: ArrayDeque<String>

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkObject(SMSFormatter.Companion)
        mockkStatic(SmsManager::class)
        every { SmsManager.getDefault() } returns smsManager

        val mockPacketList = mutableListOf("mockPacket1", "mockPacket2")
        every { SMSFormatter.formatSMS(any(), any()) } returns mockPacketList

        smsSender = SMSSender(smsKeyManager, sharedPreferences, context, smsStateReporter)

        smsRelayQueue = injectMockQueue()
    }

    @AfterEach
    fun afterAll() {
        unmockkAll()
    }

    private fun injectMockQueue(): ArrayDeque<String> {
        val smsRelayQueue = ArrayDeque<String>()
        val queueField = smsSender.javaClass.getDeclaredField("smsRelayQueue")
        queueField.isAccessible = true
        queueField.set(smsSender, smsRelayQueue)

        return smsRelayQueue
    }

    @Test
    fun `queueRelayContent should initialize sending and add packets to queue`() {
        val result = smsSender.queueRelayContent("mock message")

        verify {
            smsStateReporter.initSending(any())
        }
        Assertions.assertTrue(result)
        Assertions.assertEquals(2, smsRelayQueue.size)
    }

    @Test
    fun `sendSmsMessage should send SMS if acknowledged is false`() {
        val mockMessage = "mock SMS message"

        val result = smsSender.queueRelayContent(mockMessage)
        Assertions.assertTrue(result)

        Assertions.assertEquals(2, smsRelayQueue.size)

        smsSender.sendSmsMessage(acknowledged = false)
        verify {
            smsManager.divideMessage(any())
        }

        verify {
            smsManager.sendMultipartTextMessage(any(), any(), any(), null, null)
        }

        // Message should not be removed from queue until acknowledgement received
        Assertions.assertEquals(2, smsRelayQueue.size)
    }

    @Test
    fun `sendSmsMessage should send all SMS messages and properly handle acknowledgements`() {
        val mockStateLiveData = mockk<MutableLiveData<SmsTransmissionStates>>(relaxed = true)
        val mockMessage = "mock SMS message"
        every { smsStateReporter.state } returns mockStateLiveData

        // Queue messages to send
        smsSender.queueRelayContent(mockMessage)

        // Send initial message
        smsSender.sendSmsMessage(false)
        verify {
            smsManager.divideMessage(any())
        }

        verify {
            smsManager.sendMultipartTextMessage(any(), any(), any(), null, null)
        }

        // Acknowledgement received, send next message
        smsSender.sendSmsMessage(true)
        Assertions.assertEquals(1, smsRelayQueue.size)

        verify {
            smsManager.sendMultipartTextMessage(any(), any(), any(), null, null)
        }

        // Last acknowledgement received and no more messages to send
        smsSender.sendSmsMessage(true)
        Assertions.assertEquals(0, smsRelayQueue.size)
        verify(exactly = 1) {
            mockStateLiveData.postValue(SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE)
        }
    }

}