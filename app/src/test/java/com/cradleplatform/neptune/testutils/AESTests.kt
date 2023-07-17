package com.cradleplatform.neptune.testutils

import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncryptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class AESTests {
    @Test
    fun test_encryption_decryption() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val stringKey = AESEncryptor.generateRandomKey("test@test.com")
        val wrongStringKey = AESEncryptor.generateRandomKey("wrong@test.com")
        val key = AESEncryptor.getSecretKeyFromString(stringKey)
        val wrongKey = AESEncryptor.getSecretKeyFromString(wrongStringKey)

        val encryptedMsg = AESEncryptor.encrypt(originalMsg, key)
        Assertions.assertNotEquals(String(encryptedMsg), originalMsg)

        try {
            AESEncryptor.decrypt(encryptedMsg, wrongKey)
            Assertions.fail()
        } catch (e:Exception) {}

        val decryptedMsg = AESEncryptor.decrypt(encryptedMsg, key)
        Assertions.assertEquals(String(decryptedMsg), originalMsg)
    }

}