package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.sync.PeriodicSyncer
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.SharedPreferencesMigration
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
    private val periodicSyncer: PeriodicSyncer,
    private val smsKeyManager: SmsKeyManager,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LoginManager"
        const val ACCESS_TOKEN_KEY = "accessToken"
        const val EMAIL_KEY = "email"
        const val USERNAME_KEY = "username"
        // A list of all phone numbers for the user
        const val PHONE_NUMBERS = "phoneNumbers"
        // The current phone number of the user - will be the source of SMS messages
        const val USER_PHONE_NUMBER = "currentUserPhoneNumbers"
        // The current relay phone number - default in settings.xml - changeable from the settings
        const val RELAY_PHONE_NUMBER = "currentRelayPhoneNumbers"
        const val USER_ID_KEY = "userId"
    }

    fun isLoggedIn(): Boolean {
        sharedPreferences.run {
            if (!contains(ACCESS_TOKEN_KEY)) {
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
            // If we failed to login, return immediately
            val loginResult = restApi.authenticate(email, password)
            if (loginResult is NetworkResult.Success) {
                val loginResponse = loginResult.value
                sharedPreferences.edit(commit = true) {
                    putString(ACCESS_TOKEN_KEY, loginResponse.accessToken)
                    putInt(USER_ID_KEY, loginResponse.user.id)
                    putString(EMAIL_KEY, loginResponse.user.email)
                    putString(USERNAME_KEY, loginResponse.user.username)

                    val phoneNumbersSerialized = loginResponse.user.phoneNumbers.joinToString(",")
                    putString(PHONE_NUMBERS, phoneNumbersSerialized)
                    putString(
                        context.getString(R.string.key_vht_name),
                        loginResponse.user.name
                    )

                    if (UserRole.safeValueOf(loginResponse.user.role) == UserRole.UNKNOWN) {
                        Log.w(TAG, "server returned unrecognized role ${loginResponse.user.role}")
                    }
                    // Put the role in as-is anyway
                    putString(
                        context.getString(R.string.key_role),
                        loginResponse.user.role
                    )

                    putInt(
                        SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION,
                        SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
                    )

                    setDefaultRelayPhoneNumber()

                    // Extract and securely store the smsKey
                    val smsKey = loginResponse.user.smsKey
                    // TODO: Modify such that what ever is stored is just
                    smsKeyManager.storeSmsKey(smsKey)
                    // TODO: todo check result
                }

                periodicSyncer.startPeriodicSync()
            } else {
                return@withContext loginResult.cast()
            }
            return@withContext NetworkResult.Success(Unit, HTTP_OK)
        }
    }

    private fun setDefaultRelayPhoneNumber() {
        // get the default relay phone number from settings.xml
        val defaultRelayPhoneNumber = context.getString(R.string.settings_default_relay_phone_number)
        // set the relay phone number to the default
        sharedPreferences.edit().putString(RELAY_PHONE_NUMBER, defaultRelayPhoneNumber).apply()
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        database.run {
            clearAllTables()
        }

        periodicSyncer.endPeriodicSync()

        // Clear all the user specific information from sharedPreferences
        sharedPreferences.edit(commit = true) {
            remove(USER_PHONE_NUMBER)
            remove(ACCESS_TOKEN_KEY)
            remove("refresh_token")
            remove(USER_ID_KEY)
            remove(EMAIL_KEY)
            remove(USERNAME_KEY)
            remove(PHONE_NUMBERS)

            /* Need to do this because the database gets cleared.
             * Will re-sync all data upon next login. */
            remove(SyncAllWorker.LAST_PATIENT_SYNC)
            remove(SyncAllWorker.LAST_READING_SYNC)
            remove(SyncAllWorker.LAST_REFERRAL_SYNC)
            remove(SyncAllWorker.LAST_ASSESSMENT_SYNC)
            remove(SyncAllWorker.LAST_HEALTH_FACILITIES_SYNC)
        }
        smsKeyManager.clearSmsKey()
    }
}

/**
 * Models the response sent back by the server for /api/user/auth.
 * Not used outside of LoginManager.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    val accessToken: String,
    val user: LoginResponseUser
)

data class LoginResponseUser(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val name: String?,
    val healthFacilityName: String?,
    val phoneNumbers: List<String>,
    val smsKey: SmsKey
)

data class RefreshTokenResponse(
    val accessToken: String
)
