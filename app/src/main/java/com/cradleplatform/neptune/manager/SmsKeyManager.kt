package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cradleplatform.neptune.model.SmsKeyResponse
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.viewmodel.UserViewModel.Companion.SMS_SECRET_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty1

@Singleton
class SmsKeyManager @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var encryptedSharedPreferences: SharedPreferences

    init {
        initialize()
    }

    enum class KeyState {
        NORMAL,
        NOTFOUND,
        WARN,
        EXPIRED
    }

    private fun initialize() {
        // create a master key for EncryptedSharedPreferences
        val masterKeyAlias = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        encryptedSharedPreferences = EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun storeSmsKey(secretKey: String): Boolean {
        encryptedSharedPreferences.edit().putString(SMS_SECRET_KEY, secretKey).apply()
        return true
    }

    fun updateSmsKey(updatedSecretKey: SmsKeyResponse): Boolean {
        // parse the JSON string using Jackson
        val objectMapper = JacksonMapper.mapper
        return try {
            val smsKeyPreviousData: SmsKeyResponse = objectMapper.readValue(
                retrieveSmsKey(),
                SmsKeyResponse::class.java
            )
            smsKeyPreviousData.smsKey = updatedSecretKey.smsKey
            smsKeyPreviousData.expiryDate = updatedSecretKey.expiryDate
            smsKeyPreviousData.staleDate = updatedSecretKey.staleDate
            smsKeyPreviousData.message = updatedSecretKey.message
            val updatedSmsKeyString = convertToKeyValuePairs(smsKeyPreviousData)
            storeSmsKey(updatedSmsKeyString)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun retrieveSmsKey(): String? {
        return encryptedSharedPreferences.getString(SMS_SECRET_KEY, null)
    }

    // Used for when the user logs out
    fun clearSmsKey() {
        encryptedSharedPreferences.edit().remove(SMS_SECRET_KEY).apply()
    }

    fun validateSmsKey(smsKey: String?): KeyState {
        if (smsKey == null || smsKey == "NOTFOUND") {
            return KeyState.NOTFOUND
        }
        // parse the JSON string using Jackson
        val objectMapper = JacksonMapper.mapper
        return try {
            val smsKeyData: SmsKeyResponse = objectMapper.readValue(smsKey, SmsKeyResponse::class.java)
            when (smsKeyData.message) {
                "EXPIRED" -> {
                    KeyState.EXPIRED
                }
                "WARN" -> {
                    KeyState.WARN
                }
                "NOTFOUND" -> {
                    KeyState.NOTFOUND
                }
                else -> {
                    KeyState.NORMAL
                }
            }
        } catch (e: Exception) {
            KeyState.NOTFOUND
        }
    }

    fun getDaysUntilExpiry(smsKey: String): Int {
        val objectMapper = JacksonMapper.mapper
        val smsKeyLoginData: SmsKeyResponse = objectMapper.readValue(smsKey, SmsKeyResponse::class.java)
        val targetDate = smsKeyLoginData.expiryDate

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = Calendar.getInstance().time
        val parsedTargetDate = dateFormat.parse(targetDate) ?: return -1 // parsing failed

        val diffInMillis = parsedTargetDate.time - currentDate.time
        val daysInMillis = 1000 * 60 * 60 * 24 // # of milliseconds in a day
        val daysUntilTarget = diffInMillis / daysInMillis

        return daysUntilTarget.toInt()
    }

    fun convertToKeyValuePairs(obj: Any): String {
        val keyValuePairs = mutableListOf<String>()
        val properties = obj::class.members.filterIsInstance<KProperty1<Any, *>>()

        for (property in properties) {
            val key = property.name
            val value = property.get(obj)
            keyValuePairs.add("\"$key\": \"$value\"")
        }

        return "{${keyValuePairs.joinToString(", ")}}"
    }

    // TODO: not used
    private fun hasDatePassed(dateString: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = Date()

        try {
            val parsedDate = dateFormat.parse(dateString)
            if (parsedDate != null) {
                return parsedDate.before(currentDate)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
