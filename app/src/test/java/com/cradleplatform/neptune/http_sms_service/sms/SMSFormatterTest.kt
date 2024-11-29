package com.cradleplatform.neptune.http_sms_service.sms

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Base64
import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.model.CommonProcessedHttpRequest
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.GzipCompressor
import com.cradleplatform.neptune.utilities.SMSFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import org.json.JSONObject

internal class SMSFormatterTest {

    @Test
    fun test_encodeMsg() {
        val originalMsg = "This is a test message for encoding."
        val secretKey = "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}" // Example Base64

        mockkObject(AESEncryptor.Companion)
        every { AESEncryptor.encryptString(any(), any()) } returns "encrypted_string"

        val encodedMsg = SMSFormatter.encodeMsg(originalMsg, secretKey)

        // Validate that the Base64 encoding occurs after encryption
        val base64EncodedEncryptedMsg = Base64.getEncoder().encodeToString("encrypted_string".toByteArray())
        Assertions.assertEquals(base64EncodedEncryptedMsg, encodedMsg)
    }


    @Test
    fun testDecodeMsg_withBase64() {
        val originalMsg = "This is a test message for decoding."
        val secretKey = "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"
        val compressedMsg = GzipCompressor.compress(originalMsg)
        val encryptedMsg = "encrypted_message"

        mockkObject(AESEncryptor.Companion)
        every { AESEncryptor.decryptString(any(), any()) } returns compressedMsg

        val base64EncodedEncryptedMsg = Base64.getEncoder().encodeToString(encryptedMsg.toByteArray())
        val decodedMsg = SMSFormatter.decodeMsg(base64EncodedEncryptedMsg, secretKey)

        // Validate the decompressed and decrypted message matches the original
        Assertions.assertEquals(originalMsg, decodedMsg)
    }

    @Test
    fun testBase64KeyExtraction() {
        val base64Key = "SGVsbG8sIFdvcmxkIQ==" // Base64 for "Hello, World!"
        val secretKey = "{\"sms_key\":\"$base64Key\"}"

        val extractedKey = JSONObject(secretKey).optString("sms_key")

        Assertions.assertEquals(base64Key, extractedKey)

        val decodedKey = Base64.getDecoder().decode(extractedKey)
        Assertions.assertEquals("Hello, World!", String(decodedKey))
    }

    @Test
    fun testFormatSMS() {
        val testProcessedData = CommonProcessedHttpRequest.testData1
        val currentRequestCounter = 8581L

        val result = SMSFormatter.formatSMS(testProcessedData, currentRequestCounter)

        for (i in result.indices) {
            if (i < result.size - 1) {
                // All packets except the last should have the full size
                Assertions.assertEquals(SMSFormatter.PACKET_SIZE, result[i].length)
            } else {
                // Last packet might be shorter
                Assertions.assertTrue(SMSFormatter.PACKET_SIZE >= result[i].length)
            }
        }
        // Validate that the formatted packets match the expected structure
        for (i in result.indices) {
            Assertions.assertEquals(CommonProcessedHttpRequest.formattedTestData1[i], result[i])
        }
    }

    //TODO: Test encryptString() function
}