package com.cradleVSA.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.ext.jackson.parseJsonArrayFromStream
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkException
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.sync.SyncStepperImplementation
import com.cradleVSA.neptune.utilitiles.SharedPreferencesMigration
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
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
        // Send a request to the authentication endpoint to login
        //
        // If we failed to login, return immediately
        val loginResult = restApi.authenticate(email, password)
        when (loginResult) {
            is Success -> {
                // Fail the login if the token and userId are not in the response for
                // whatever reason
                val (token, userId) = try {
                    loginResult.value.getString("token") to
                        loginResult.value.getInt("userId")
                } catch (e: JSONException) {
                    return@withContext NetworkException(e)
                }

                // The name is not as important, so we don't fail the login if there isn't a name
                // in the response.
                val name = try {
                    loginResult.value.getString("firstName")
                } catch (e: JSONException) {
                    null
                }

                sharedPreferences.edit(commit = true) {
                    putString(TOKEN_KEY, token)
                    putInt(USER_ID_KEY, userId)
                    putString(EMAIL_KEY, email)
                    putString(context.getString(R.string.key_vht_name), name)
                    putInt(
                        SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION,
                        SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
                    )
                }
            }
            else -> return@withContext loginResult.cast()
        }

        // Once successfully logged in, download the user's patients and health
        // facilities in parallel.
        // We will use this later as the last synced timestamp.
        val loginTime = UnixTimestamp.now

        val patientsResultAsync = async {
            val startTime = System.currentTimeMillis()

            val channel = Channel<PatientAndReadings>()
            val databaseJob = launch {
                database.withTransaction {
                    for (patientAndReadings in channel) {
                        patientManager.addPatientWithReadings(
                            patientAndReadings.patient,
                            patientAndReadings.readings,
                            areReadingsFromServer = true,
                            isPatientNew = true
                        )
                    }
                    Log.d(TAG, "patient & reading database job is done")
                }
            }
            val result = restApi.getAllPatientsStreaming { inputStream ->
                // Parse JSON strings directly from the HTTPUrlConnection's input stream to avoid
                // dealing with a ByteArray of an entire JSON array in memory and trying to convert
                // that into a String.
                val reader = JacksonMapper.readerForPatientAndReadings
                reader.parseJsonArrayFromStream(inputStream) { parser ->
                    try {
                        val patientAndReadings = reader.readValue<PatientAndReadings>(parser)
                        channel.send(patientAndReadings)
                    } catch (e: IOException) {
                        Log.e(TAG, "failed to parse JSON for patients and readings", e)
                        // Propagate exceptions to the Http class so that it can log it
                        throw e
                    }
                }
            }

            when (result) {
                is Success -> {
                    channel.close()
                    Log.d(TAG, "Patient and readings download successful!")
                }
                is Failure -> {
                    channel.cancel()
                    Log.e(
                        TAG,
                        "Patient and readings download failed, got status code: " +
                            "${result.statusCode}"
                    )
                }
                is NetworkException -> {
                    channel.cancel()
                    Log.e(
                        TAG,
                        "Patient and readings download failed, encountered exception",
                        result.cause
                    )
                }
            }

            databaseJob.join()
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Patient/readings download overall took ${endTime - startTime} ms")
            return@async result
        }

        // for unit testing purposes
        if (!parallelDownload) {
            patientsResultAsync.join()
        }

        // TODO: Maybe make it so that the health facility the server sends back cannot
        //       be removed by the user?
        // TODO: Show some dialog to select a health facility
        val healthFacilityResultAsync = async {
            val defaultHealthFacilityName = try {
                loginResult.value.getString("healthFacilityName")
            } catch (e: JSONException) {
                null
            }

            val channel = Channel<HealthFacility>()
            val databaseJob = launch {
                database.withTransaction {
                    for (healthFacility in channel) {
                        if (healthFacility.name == defaultHealthFacilityName) {
                            healthFacility.isUserSelected = true
                        }
                        healthFacilityManager.add(healthFacility)
                    }
                    Log.d(TAG, "health facility database job is done")
                }
            }
            val result = restApi.getAllHealthFacilities { inputStream ->
                val reader = JacksonMapper.readerForHealthFacility
                reader.parseJsonArrayFromStream(inputStream) { parser ->
                    try {
                        val healthFacility = reader.readValue<HealthFacility>(parser)
                        channel.send(healthFacility)
                    } catch (e: IOException) {
                        Log.e(TAG, "failed to parse JSON for health facility", e)
                        // Propagate exceptions to the Http class so that it can log it
                        throw e
                    }
                }
            }

            when (result) {
                is Success -> {
                    channel.close()
                    Log.d(TAG, "Health facility download successful!")
                }
                is Failure -> {
                    channel.cancel()
                    Log.e(
                        TAG,
                        "Health facility download failed, got status code: " +
                            "${result.statusCode}"
                    )
                }
                is NetworkException -> {
                    channel.cancel()
                    Log.e(
                        TAG,
                        "Health facility download failed, encountered exception",
                        result.cause
                    )
                }
            }
            databaseJob.join()
            return@async result
        }

        joinAll(patientsResultAsync, healthFacilityResultAsync)
        if (patientsResultAsync.await() is Success) {
            sharedPreferences.edit(commit = true) {
                putLong(SyncStepperImplementation.LAST_SYNC, loginTime)
            }
        }

        Success(Unit, HTTP_OK)
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
