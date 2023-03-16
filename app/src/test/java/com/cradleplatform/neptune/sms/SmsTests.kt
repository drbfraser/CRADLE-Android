package com.cradleplatform.neptune.sms

import android.util.Base64
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.GzipCompressor
import com.cradleplatform.neptune.utilities.RelayAction
import com.cradleplatform.neptune.utilities.SMSFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class SmsTests {
    @Test
    fun `test_compression_encryption_encoding_decoding_decryption_decompression`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val originalSize = originalMsg.toByteArray(Charsets.UTF_8).size
        val secretKey = AESEncryptor.generateRandomKey()
        val wrongKey = AESEncryptor.generateRandomKey()

        // compress first as gzip utilizes pattern recognition to reduce size
        val compressedMsg = GzipCompressor.compress(originalMsg)
        Assertions.assertTrue(compressedMsg.size < originalSize)

        val encryptedMsg = AESEncryptor.encrypt(compressedMsg, secretKey)
        Assertions.assertTrue(encryptedMsg.size < originalSize)

        val encodedMsg = Base64.encodeToString(encryptedMsg, 0)
        val decodedMsg = Base64.decode(encodedMsg, 0)
        Assertions.assertEquals(String(encryptedMsg), String(decodedMsg))

        try {
            AESEncryptor.decrypt(decodedMsg, wrongKey)
            Assertions.fail()
        } catch (e: Exception) {
        }

        val decryptedMsg = AESEncryptor.decrypt(decodedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg)
    }

    @Test
    fun `test_sms_packet_formatting_size`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = AESEncryptor.generateRandomKey()

        val formattedMsg = AESEncryptor.encrypt(GzipCompressor.compress(originalMsg), secretKey)
        val encodedMsg = Base64.encodeToString(formattedMsg, 0)

        val packets = SMSFormatter.formatSMS(encodedMsg, 0L)
        val maxPacketSize = 153 * 2
        val maxPacketCount = 100

        Assertions.assertTrue(packets.size < maxPacketCount)
        for (packet: String in packets) {
            Assertions.assertTrue(packet.length <= maxPacketSize)
        }
    }

    @Test
    fun `test_sms_packet_formatting_decoding`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = AESEncryptor.generateRandomKey()
        val requestCounter = 0L

        val encodedMsg = SMSFormatter.encodeMsg(
            originalMsg,
            secretKey
        )

        val packets = SMSFormatter.formatSMS(encodedMsg, requestCounter)
        val packetMsg = SMSFormatter.parseSMS(packets)
        // limit to 5 as there are 4 header components and 1 encrypted request data
        val packetComponents = packetMsg.split('-', limit = 5)
        println(packetComponents)

        // verify the header values/length are correct
        Assertions.assertEquals(SMSFormatter.SMS_TUNNEL_PROTOCOL_VERSION, packetComponents[0])
        Assertions.assertEquals(SMSFormatter.MAGIC_STRING, packetComponents[1])
        Assertions.assertEquals(
            requestCounter.toString().padStart(6, '0'), packetComponents[2]
        )
        Assertions.assertEquals(SMSFormatter.FRAGMENT_HEADER_LENGTH, packetComponents[3].length)
        val encodedRequestData = packetComponents[4].split(Regex("[0-9]{3}-"))
            .joinToString(separator = "")
        Assertions.assertEquals(encodedMsg, encodedRequestData)
    }
}