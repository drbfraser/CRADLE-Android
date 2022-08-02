package com.cradleplatform.neptune.sms

import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncrypter
import com.cradleplatform.neptune.utilities.GzipCompressor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class SmsTests {
    @Test
    fun `test_compression_encryption_decryption_decompression`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val originalSize = originalMsg.toByteArray(Charsets.UTF_8).size
        val secretKey = AESEncrypter.generateRandomKey()

        // compress first as gzip utilizes pattern recognition to reduce size
        val compressedMsg = GzipCompressor.compress(originalMsg)
        Assertions.assertTrue(compressedMsg.size < originalSize)

        val encryptedMsg = AESEncrypter.encrypt(compressedMsg, secretKey)
        Assertions.assertTrue(encryptedMsg.size < originalSize)

        val decryptedMsg = AESEncrypter.decrypt(encryptedMsg, secretKey)
        Assertions.assertEquals(String(compressedMsg), String(decryptedMsg))

        val decompressedMsg = GzipCompressor.decompress(decryptedMsg)
        Assertions.assertEquals(originalMsg, decompressedMsg)
    }
}