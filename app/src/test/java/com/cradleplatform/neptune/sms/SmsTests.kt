package com.cradleplatform.neptune.sms

import android.util.Base64
import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncrypter
import com.cradleplatform.neptune.utilities.GzipCompressor
import com.cradleplatform.neptune.utilities.RelayAction
import com.cradleplatform.neptune.utilities.SMSMessageFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class SmsTests {
    @Test
    fun `test_compression_encryption_encoding_decoding_decryption_decompression`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val originalSize = originalMsg.toByteArray(Charsets.UTF_8).size
        val secretKey = AESEncrypter.generateRandomKey()
        val wrongKey = AESEncrypter.generateRandomKey()

        // compress first as gzip utilizes pattern recognition to reduce size
        val compressedMsg = GzipCompressor.compress(originalMsg)
        Assertions.assertTrue(compressedMsg.size < originalSize)

        val encryptedMsg = AESEncrypter.encrypt(compressedMsg, secretKey)
        Assertions.assertTrue(encryptedMsg.size < originalSize)

        val encodedMsg = Base64.encodeToString(encryptedMsg, 0)
        val decodedMsg = Base64.decode(encodedMsg, 0)
        Assertions.assertEquals(String(encryptedMsg), String(decodedMsg))

        try {
            AESEncrypter.decrypt(decodedMsg, wrongKey)
            Assertions.fail()
        } catch (e:Exception) {}

        val decryptedMsg = AESEncrypter.decrypt(decodedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg)
    }

    @Test
    fun `test_sms_packet_formatting_size`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = AESEncrypter.generateRandomKey()
        val action = RelayAction.REFERRAL
        val msgWithAction = "$action|$originalMsg"

        val formattedMsg = AESEncrypter.encrypt(GzipCompressor.compress(msgWithAction), secretKey)
        val encodedMsg = Base64.encodeToString(formattedMsg, 0)

        val packets = SMSMessageFormatter.formatSMS(encodedMsg)
        val maxPacketSize = 160
        val maxPacketCount = 100

        Assertions.assertTrue(packets.size < maxPacketCount)
        for (packet: String in packets) {
            Assertions.assertTrue(packet.length < maxPacketSize)
        }
    }

    @Test
    fun `test_sms_packet_formatting_decoding`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val secretKey = AESEncrypter.generateRandomKey()
        val action = RelayAction.REFERRAL
        val msgWithAction = "$action|$originalMsg"
        val wrongKey = AESEncrypter.generateRandomKey()

        val compressedMsg = GzipCompressor.compress(msgWithAction)
        val formattedMsg = AESEncrypter.encrypt(compressedMsg, secretKey)
        val encodedMsg = Base64.encodeToString(formattedMsg, 0)

        val packets = SMSMessageFormatter.formatSMS(encodedMsg)
        val packetMsg = SMSMessageFormatter.parseSMS(packets)

        Assertions.assertEquals(packetMsg, encodedMsg)

        val decodedMsg = Base64.decode(packetMsg, 0)
        Assertions.assertEquals(String(formattedMsg), String(decodedMsg))

        try {
            AESEncrypter.decrypt(decodedMsg, wrongKey)
            Assertions.fail()
        } catch (e:Exception) {}

        val decryptedMsg = AESEncrypter.decrypt(decodedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg.substringAfter('|'))
        Assertions.assertEquals("$action", decompressedMsg.substringBefore('|'))
    }
}