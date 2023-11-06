package com.cradleplatform.neptune.http_sms_service.sms

import android.util.Base64
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.model.CommonProcessedHttpRequest
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.GzipCompressor
import com.cradleplatform.neptune.utilities.RelayAction
import com.cradleplatform.neptune.utilities.SMSFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify

internal class SMSFormatterTest {

    @Test
    fun test_encodeMsg() {
        // Test that encodeMsg() calls encryptString() correctly and returns the right value
        // This code only tests the functionality of encodeMsg() and not encryptString()
        // We need to implement a test in order to verify the functionality of encryptString()
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"

        mockkObject(AESEncryptor.Companion)
        val encryptStringReturn = "encryptStringReturn"
        every { AESEncryptor.encryptString(any(), any()) } returns encryptStringReturn
        val encodedMsg = SMSFormatter.encodeMsg(
            originalMsg,
            secretKey
        )
        Assertions.assertEquals(encodedMsg, encryptStringReturn)

        verify { AESEncryptor.encryptString(GzipCompressor.compress(originalMsg), "SGVsbG8sIFdvcmxkIQ==") }
    }

    @Test
    fun testFormatSMS() {
        val testProcessedData = CommonProcessedHttpRequest.testData1
        val currentRequestCounter = 8581L

        val result = SMSFormatter.formatSMS(testProcessedData, currentRequestCounter)

        for (i in 0 until result.size) {
            if (i < result.size - 1)
                Assertions.assertEquals(SMSFormatter.PACKET_SIZE, result[i].length)
            else
                Assertions.assertTrue(SMSFormatter.PACKET_SIZE >= result[i].length)

            Assertions.assertEquals(CommonProcessedHttpRequest.formattedTestData1[i], result[i])
        }

    }
    //TODO: Test encryptString() function
}