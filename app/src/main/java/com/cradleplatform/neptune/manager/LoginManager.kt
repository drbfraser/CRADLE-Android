package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.utilities.SharedPreferencesMigration
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject

/**
 * Manages user login credentials authenticating with the server
 */
class LoginManager @Inject constructor(
    private val restApi: RestApi, // only for authenticating calls
    private val sharedPreferences: SharedPreferences,
    private val database: CradleDatabase, // only for clearing database on logout
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LoginManager"
        const val TOKEN_KEY = "token"
        const val EMAIL_KEY = "loginEmail"
        const val PHONE_NUMBERS = "phoneNumbers"
        const val CURRENT_PHONE_NUMBER = "currentPhoneNumbers" // Change this to relay phone number
        const val SMS_K_Key = "sms_key_key"
        const val USER_ID_KEY = "userId"
    }

    fun isLoggedIn(): Boolean {
        sharedPreferences.run {
            if (!contains(TOKEN_KEY)) {
                return false
            }
            if (!contains(USER_ID_KEY)) {
                return false
            }
        }
        return true
    }

    private val loginMutex = Mutex()

    /**
     * Performs the complete login sequence required to log a user in
     *
     * @param email the email to login with
     * @param password the password to login with
     * @return a [NetworkResult.Success] variant if the user was able to login successfully,
     *  otherwise a [[NetworkResult.Failure] or [[NetworkResult.NetworkException] will be returned
     */
    suspend fun login(
        email: String,
        password: String
    ): NetworkResult<Unit> = withContext(Dispatchers.Default) {
        // Prevent logging in twice
        loginMutex.withLock {
            if (isLoggedIn()) {
                Log.w(TAG, "trying to login twice!")
                return@withContext NetworkResult.NetworkException(Exception("already logged in"))
            }

            // Send a request to the authentication endpoint to login
            //
            // If we failed to login, return immediately
            val loginResult = restApi.authenticate(email, password)
            if (loginResult is NetworkResult.Success) {
                val loginResponse = loginResult.value
                val smsKey = loginResponse.smsKey // Extract smsKey

                // create a master key for EncryptedSharedPreferences
                val masterKeyAlias = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                // create EncryptedSharedPreferences
                val encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    "encrypted_prefs",
                    masterKeyAlias,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // store smsKey in EncryptedSharedPreferences
                encryptedPrefs.edit(commit = true) {
                    putString(SMS_K_Key, smsKey) // Store smsKey only
                }
                
                sharedPreferences.edit(commit = true) {
                    putString(TOKEN_KEY, loginResponse.token)
                    putInt(USER_ID_KEY, loginResponse.userId)
                    putString(EMAIL_KEY, loginResponse.email)
                    putString(CURRENT_PHONE_NUMBER, "+15555215554")  // TODO: change to dynamic relay phone number from settings

                    val phoneNumbersSerialized = loginResponse.phoneNumbers.joinToString(",")
                    putString(PHONE_NUMBERS, phoneNumbersSerialized)
                    putString(
                        context.getString(R.string.key_vht_name),
                        loginResponse.firstName
                    )

                    if (UserRole.safeValueOf(loginResponse.role) == UserRole.UNKNOWN) {
                        Log.w(TAG, "server returned unrecognized role ${loginResponse.role}")
                    }
                    // Put the role in as-is anyway
                    putString(
                        context.getString(R.string.key_role),
                        loginResponse.role
                    )

                    putInt(
                        SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION,
                        SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
                    )
                }
            } else {
                return@withContext loginResult.cast()
            }

            return@withContext NetworkResult.Success(Unit, HTTP_OK)
        }
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        val (hostname, port) =
            sharedPreferences.getString(context.getString(R.string.key_server_hostname), null) to
                sharedPreferences.getString(context.getString(R.string.key_server_port), null)

        sharedPreferences.edit(commit = true) {
            clear()
            hostname?.let { putString(context.getString(R.string.key_server_hostname), it) }
            port?.let { putString(context.getString(R.string.key_server_port), it) }
        }

        database.run {
            clearAllTables()
        }
    }
}

/**
 * Models the response sent back by the server for /api/user/auth.
 * Not used outside of LoginManager.
 * TODO: Store refresh token and use it (refer to issue #52)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    @JsonProperty
    val email: String,
    @JsonProperty
    val role: String,
    @JsonProperty
    val firstName: String?,
    @JsonProperty
    val healthFacilityName: String?,
    @JsonProperty
    val phoneNumbers: List<String>,
    @JsonProperty
    val userId: Int,
    @JsonProperty
    val token: String,
    @JsonProperty
    val smsKey: String,
)
