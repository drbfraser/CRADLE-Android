package com.cradleplatform.neptune.utilities

import android.content.res.Resources
import android.util.Base64
import com.cradleplatform.neptune.R
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class AESEncrypter {
    companion object {
        private const val TRANSFORMATION = "AES/ECB/PKCS5PADDING"

        fun getSecretKeyFromSetting(): SecretKey {
            val settingKey = Resources.getSystem().getString(R.string.aes_secret_key)
            val encodedKey = Base64.decode(settingKey, Base64.DEFAULT)
            return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
        }

        fun generateRandomKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            return keyGen.generateKey()
        }

        fun encrypt(msgInByteArray: ByteArray, key: SecretKey): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            return cipher.doFinal(msgInByteArray)
        }

        fun encrypt(msg: String, key: SecretKey): ByteArray {
            val msgInByteArray = msg.toByteArray(Charsets.UTF_8)

            return encrypt(msgInByteArray, key)
        }

        fun decrypt(msgInByteArray: ByteArray, key: SecretKey): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key)

            return cipher.doFinal(msgInByteArray)
        }
    }
}
