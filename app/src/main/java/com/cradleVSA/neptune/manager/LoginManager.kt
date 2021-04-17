package com.cradleVSA.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.model.UserRole
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkException
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.net.SyncException
import com.cradleVSA.neptune.sync.SyncWorker
import com.cradleVSA.neptune.utilitiles.SharedPreferencesMigration
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.utilitiles.nullIfEmpty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject

/**
 * Manages logging the user into the server and setting up the application once
 * successfully logged in.
 */
class LoginManager @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences,
    private val database: CradleDatabase,
    private val patientManager: PatientManager,
    private val healthFacilityManager: HealthFacilityManager,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "LoginManager"
        const val TOKEN_KEY = "token"
        const val EMAIL_KEY = "loginEmail"
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
     * Performs the complete login sequence required to log a user in and
     * initialize the application for use.
     *
     * @param email the email to login with
     * @param password the password to login with
     * @param parallelDownload whether to download patient + readings and health facilities in
     * parallel. True by default. (For unit testing purposes to get around a problem.)
     * @return a [Success] variant if the user was able to login successfully,
     *  otherwise a [Failure] or [NetworkException] will be returned
     */
    suspend fun login(
        email: String,
        password: String,
        parallelDownload: Boolean = true
    ) = withContext(Dispatchers.Default) {
        // Prevent logging in twice
        loginMutex.withLock {
            if (isLoggedIn()) {
                Log.w(TAG, "trying to login twice!")
                return@withContext NetworkException(Exception("already logged in"))
            }

            // Send a request to the authentication endpoint to login
            //
            // If we failed to login, return immediately
            val loginResult = restApi.authenticate(email, password)
            if (loginResult is Success) {
                val loginResponse = loginResult.value
                sharedPreferences.edit(commit = true) {
                    putString(TOKEN_KEY, loginResponse.token)
                    putInt(USER_ID_KEY, loginResponse.userId)
                    putString(EMAIL_KEY, loginResponse.email)
                    putString(
                        context.getString(R.string.key_vht_name),
                        loginResponse.firstName?.nullIfEmpty()
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

            // Once successfully logged in, download the user's patients and health
            // facilities in parallel.
            // We will use this later as the last synced timestamp.
            val loginTime = UnixTimestamp.now

            val patientsResultAsync = async { downloadPatients() }

            // for unit testing purposes
            if (!parallelDownload) {
                patientsResultAsync.join()
            }

            // TODO: Maybe make it so that the health facility the server sends back cannot
            //       be removed by the user?
            // TODO: Show some dialog to select a health facility
            val healthFacilityResultAsync = async {
                downloadHealthFacilities(loginResult.value.healthFacilityName)
            }

            joinAll(patientsResultAsync, healthFacilityResultAsync)
            if (patientsResultAsync.await() is Success) {
                sharedPreferences.edit(commit = true) {
                    putString(SyncWorker.LAST_PATIENT_SYNC, loginTime.toString())
                    putString(SyncWorker.LAST_READING_SYNC, loginTime.toString())
                }
            }

            // TODO: Actually report any failures instead of lettting the user pass
            //  It might be better to just split the login manager so that this function just
            //  handles the initial login, and then the patient and health facility download can
            //  be done in another activity/fragment.
            return@withContext Success(Unit, HTTP_OK)
        }
    }

    private suspend fun downloadHealthFacilities(
        defaultHealthFacilityName: String?
    ): NetworkResult<Unit> = coroutineScope {
        val channel = Channel<HealthFacility>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (healthFacility in channel) {
                        if (healthFacility.name == defaultHealthFacilityName) {
                            healthFacility.isUserSelected = true
                        }
                        healthFacilityManager.add(healthFacility)
                    }
                    Log.d(TAG, "health facility database job is done")
                }
            } catch (e: SyncException) {
                Log.e(TAG, "failed to download health facilities", e)
            }
        }
        val result = restApi.getAllHealthFacilities(channel)

        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()
        return@coroutineScope result
    }

    private suspend fun downloadPatients(): NetworkResult<Unit> = coroutineScope {
        val startTime = System.currentTimeMillis()

        val channel = Channel<PatientAndReadings>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (patientAndReadings in channel) {
                        patientManager.addPatientWithReadings(
                            patientAndReadings.patient,
                            patientAndReadings.readings,
                            areReadingsFromServer = true
                        )
                    }
                    Log.d(TAG, "patient & reading database job is successful")
                }
            } catch (e: SyncException) {
                Log.d(TAG, "patient & reading database job failed")
            }
        }
        val result = restApi.getAllPatients(channel)
        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Patient/readings download overall took ${endTime - startTime} ms")
        return@coroutineScope result
    }

    /**
     * Close the channel for type [T] if the [result] is a [Success], otherwise cancels it.
     * Note: [RestApi] already handles channel closing, so it's likely this function isn't really
     * useful.
     */
    private inline fun <reified T> closeOrCancelChannelByResult(
        result: NetworkResult<Unit>,
        channel: Channel<T>
    ) {
        val prefix = T::class.java.simpleName
        when (result) {
            is Success -> {
                channel.close()
                Log.d(TAG, "$prefix download successful!")
            }
            is Failure -> {
                channel.cancel()
                Log.e(TAG, "$prefix download failed; status code: ${result.statusCode}")
            }
            is NetworkException -> {
                channel.cancel()
                Log.e(TAG, "$prefix download failed; exception", result.cause)
            }
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
 * TODO: Store refresh token and use it
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginResponse(
    val email: String,
    val role: String,
    val firstName: String?,
    val healthFacilityName: String?,
    val userId: Int,
    val token: String
)
