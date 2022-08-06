package com.cradleplatform.neptune.testutils

import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncrypter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class AESTests {
    @Test
    fun `test_encryption_decryption`() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val key = AESEncrypter.generateRandomKey()
        val wrongKey = AESEncrypter.generateRandomKey()

        val encryptedMsg = AESEncrypter.encrypt(originalMsg, key)
        Assertions.assertNotEquals(String(encryptedMsg), originalMsg)

        try {
            AESEncrypter.decrypt(encryptedMsg, wrongKey)
            Assertions.fail()
        } catch (e:Exception) {}

        val decryptedMsg = AESEncrypter.decrypt(encryptedMsg, key)
        Assertions.assertEquals(String(decryptedMsg), originalMsg)
    }

}