package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cradleplatform.neptune.viewmodel.UserViewModel.Companion.SMS_SECRET_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

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

    fun storeSmsKey(smsKey: SmsKey): Boolean {
        val smsKeyJson = Json.encodeToString(smsKey)
        encryptedSharedPreferences.edit().putString(SMS_SECRET_KEY, smsKeyJson).apply()
        return true
    }

    fun retrieveSmsKey(): SmsKey? {
        val smsKeyJson: String = encryptedSharedPreferences.getString(SMS_SECRET_KEY, null) ?: return null
        return Json.decodeFromString<SmsKey>(smsKeyJson)
    }

    // Used for when the user logs out
    fun clearSmsKey() {
        encryptedSharedPreferences.edit().remove(SMS_SECRET_KEY).apply()
    }

    fun validateSmsKey(): KeyState {
        // parse the JSON string using Jackson
        return try {
            val smsKey: SmsKey = retrieveSmsKey()!!
            when (smsKey.message) {
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

    fun getDaysUntilExpiry(): Int {
        val smsKey = retrieveSmsKey() ?: return -1
        val targetDate = smsKey.expiryDate

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = Calendar.getInstance().time
        val parsedTargetDate = dateFormat.parse(targetDate) ?: return -1 // parsing failed

        val diffInMillis = parsedTargetDate.time - currentDate.time
        val daysInMillis = 1000 * 60 * 60 * 24 // # of milliseconds in a day
        val daysUntilTarget = diffInMillis / daysInMillis

        return daysUntilTarget.toInt()
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

@Serializable
data class SmsKey(
    val key: String,
    val message: String,
    val expiryDate: String,
    val staleDate: String
)
