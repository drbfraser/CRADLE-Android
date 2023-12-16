package com.cradleplatform.neptune.utilities

import android.util.Base64
import android.util.Log
import okio.utf8Size
import java.lang.IllegalArgumentException
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
class AESEncryptor {
    companion object {
        private const val TAG = "AESEncryptor"
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val ivSize = 16

        ////////////////////////////////////////////////////////////////////////////////////////////

        fun encryptString(plaintext: ByteArray, secretKeyHex: String): String {
            val keyByteArray = secretKeyHex.decodeHex()
            return if (keyByteArray != null) {
                val keySpec = SecretKeySpec(keyByteArray, "AES")
                val (cipherText, iv) = encryptMsg(plaintext, keySpec)

                val ivAndCipherText = iv + cipherText // Concatenate IV and cipherText

                byteArrayToHexString(ivAndCipherText)
            } else {
                ""
            }
        }

        /**
         * Decrypt the body of encrypted SMS content into decrypted ByteArray for decompression.
         */
        fun decryptString(ciphertext: String, secretKeyHex: String): ByteArray {
            val keyByteArray = secretKeyHex.decodeHex()
            val iv = ciphertext.substring(0, ivSize*2).decodeHex()
            if (iv != null) {
                assert(iv.size == ivSize)
            }
            val cipherTextSubstring = ciphertext.substring(startIndex = ivSize*2)
            Log.d(TAG, "cipherTextSubString = $cipherTextSubstring " +
                    "\ncipherTextSubstring Length = ${cipherTextSubstring.utf8Size()}")
            val ciphertextByteArray = ciphertext.substring(ivSize*2).decodeHex()
            return if (keyByteArray != null && iv != null && ciphertextByteArray != null) {
                val keySpec = SecretKeySpec(keyByteArray, "AES")
                decryptMsg(ciphertextByteArray, iv, keySpec)
            } else {
                Log.e(TAG, "Something is null: keyByteArray ${keyByteArray.toString()}, " +
                        "iv: ${iv.toString()}, " +
                        "ciphertextByteArray: ${ciphertextByteArray.toString()}")
                return ByteArray(0)
            }
        }

        private fun String.decodeHex(): ByteArray? {
            if (length % 2 != 0) {
                return null
            }
            return chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        private fun byteArrayToHexString(byteArray: ByteArray): String {
            val sb = StringBuilder(byteArray.size * 2)
            for (byte in byteArray) {
                sb.append(String.format("%02X", byte))
            }
            return sb.toString()
        }

        private fun encryptMsg(plaintext: ByteArray, keySpec: SecretKeySpec): Pair<ByteArray, ByteArray> {
            val randomIV = ByteArray(ivSize)
            val random = SecureRandom()
            random.nextBytes(randomIV)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val ivSpec = IvParameterSpec(randomIV)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

            val cipherText = cipher.doFinal(plaintext)
            return Pair(cipherText, randomIV)
        }

        private fun decryptMsg(
            ciphertext: ByteArray,
            iv: ByteArray,
            keySpec: SecretKeySpec
        ): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            return cipher.doFinal(ciphertext)
        }

        ////////////////////////////////////////////////////////////////////////////////////////////

        // TODO: REMOVE/REPLACE - CHANGE TEST
        fun getSecretKeyFromString(settingKey: String): SecretKey {
            val encodedKey = Base64.decode(settingKey, Base64.DEFAULT)
            return SecretKeySpec(encodedKey, 0, encodedKey.size, "AES")
        }

        // TODO: REMOVE SOS - CHANGE TEST
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

        // TODO: REPLACE/ REMOVE - CHANGE TEST
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
