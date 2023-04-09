package com.cradleplatform.neptune.testutils

import android.util.Base64
import com.cradleplatform.neptune.model.CommonPatientReferralJsons
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.SMSFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

class AESTests {
    @Test
    fun test_encryption_decryption() {
        val originalMsg = CommonPatientReferralJsons.patientWithStandaloneReferral.first
        val key = AESEncryptor.generateRandomKey()
        val wrongKey = AESEncryptor.generateRandomKey()

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