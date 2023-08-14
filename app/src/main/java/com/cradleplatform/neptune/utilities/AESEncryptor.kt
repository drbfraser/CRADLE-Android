package com.cradleplatform.neptune.utilities

import android.util.Base64
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
class AESEncryptor {
    companion object {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val ivSize = 16

        private fun String.decodeHex(): ByteArray? {
            if (length % 2 != 0) {
                return null
            }
            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }


        // TODO: REPLACE
        fun getSecretKeyFromString(settingKey: String): SecretKey {
            val encodedKey = Base64.decode(settingKey, Base64.DEFAULT)
            return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
        }

        // TODO: REMOVE SOS
        fun generateRandomKey(email: String): String {
            val hashedKey = MessageDigest.getInstance("SHA-256")
                .digest(email.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val keySize = 32 // specify the desired key size here
            return hashedKey.substring(0, keySize)
        }

        // TODO: REPLACE
        private fun generateRandomIV(): ByteArray {
            val iv = ByteArray(ivSize)
            val secureRandom = SecureRandom()
            secureRandom.nextBytes(iv)
            return iv
        }

        // TODO: REPLACE
        fun encrypt(msgInByteArray: ByteArray, key: SecretKey): ByteArray {
            val iv = generateRandomIV()
            val ivSpec = IvParameterSpec(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)

            val encryptedMsg = cipher.doFinal(msgInByteArray)
            val fullCipher = ByteArray(iv.size + encryptedMsg.size)

            System.arraycopy(iv, 0, fullCipher, 0, iv.size)
            System.arraycopy(encryptedMsg, 0, fullCipher, iv.size, encryptedMsg.size)
            return fullCipher
        }

        // TODO: REPLACE
        fun encrypt(msg: String, key: SecretKey): ByteArray {
            val msgInByteArray = msg.toByteArray(Charsets.UTF_8)

            return encrypt(msgInByteArray, key)
        }

        // TODO: REPLACE
        fun decrypt(msgInByteArray: ByteArray, key: SecretKey): ByteArray {
            val iv = msgInByteArray.copyOfRange(0, ivSize)
            val ivSpec = IvParameterSpec(iv)
            val encryptedMsg = msgInByteArray.copyOfRange(ivSize, msgInByteArray.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)

            return cipher.doFinal(encryptedMsg)
        }
    }
}
