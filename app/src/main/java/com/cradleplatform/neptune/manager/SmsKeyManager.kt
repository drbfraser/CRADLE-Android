package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cradleplatform.neptune.viewmodel.UserViewModel.Companion.SMS_SECRET_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsKeyManager @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var encryptedSharedPreferences: SharedPreferences

    init {
        initialize()
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

    fun storeSmsKey(secretKey: String) {
        encryptedSharedPreferences.edit().putString(SMS_SECRET_KEY, secretKey).apply()
    }

    fun retrieveSmsKey(): String? {
        return encryptedSharedPreferences.getString(SMS_SECRET_KEY, null)
    }

    // Used for when the user logs out
    fun clearSmsKey() {
        encryptedSharedPreferences.edit().remove(SMS_SECRET_KEY).apply()
    }

    // TODO: Stale and Expiry
}
