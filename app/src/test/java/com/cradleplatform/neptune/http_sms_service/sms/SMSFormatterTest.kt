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

internal class SMSFormatterTest {

    @Test
    fun test_encodeMsg() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val stringKey = AESEncryptor.generateRandomKey("test@test.com")
        val key = AESEncryptor.getSecretKeyFromString(stringKey)

        val encodedMsg = SMSFormatter.encodeMsg(
            originalMsg,
            key
        )
        Assertions.assertNotEquals(encodedMsg, originalMsg)

        val decodedMsg = Base64.decode(encodedMsg, 0)
        val compressedPlaintext = AESEncryptor.decrypt(decodedMsg, key)
        val plaintext = GzipCompressor.decompress(compressedPlaintext)

        Assertions.assertEquals(originalMsg, plaintext)
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

}