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
        } catch (e:Exception) {}

        val decryptedMsg = AESEncryptor.decrypt(decodedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg)
    }

    @Test
    fun `test_sms_packet_formatting_size`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = AESEncryptor.generateRandomKey()
        val action = RelayAction.REFERRAL
        val msgWithAction = "$action|$originalMsg"

        val formattedMsg = AESEncryptor.encrypt(GzipCompressor.compress(msgWithAction), secretKey)
        val encodedMsg = Base64.encodeToString(formattedMsg, 0)

        val packets = SMSFormatter.formatSMS(encodedMsg, Http.Method.POST, 0L)
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
        val action = RelayAction.REFERRAL
        val msgWithAction = "$action|$originalMsg"
        val wrongKey = AESEncryptor.generateRandomKey()

        val compressedMsg = GzipCompressor.compress(msgWithAction)
        val formattedMsg = AESEncryptor.encrypt(compressedMsg, secretKey)
        val encodedMsg = Base64.encodeToString(formattedMsg, 0)

        val packets = SMSFormatter.formatSMS(encodedMsg, Http.Method.POST, 0L)
        val packetMsg = SMSFormatter.parseSMS(packets)

        Assertions.assertEquals(packetMsg, encodedMsg)

        val decodedMsg = Base64.decode(packetMsg, 0)
        Assertions.assertEquals(String(formattedMsg), String(decodedMsg))

        try {
            AESEncryptor.decrypt(decodedMsg, wrongKey)
            Assertions.fail()
        } catch (e:Exception) {}

        val decryptedMsg = AESEncryptor.decrypt(decodedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg.substringAfter('|'))
        Assertions.assertEquals("$action", decompressedMsg.substringBefore('|'))
    }
}