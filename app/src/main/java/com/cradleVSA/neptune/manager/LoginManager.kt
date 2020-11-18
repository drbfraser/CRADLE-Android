package com.cradleVSA.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkException
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.sync.SyncStepperImplementation
import com.cradleVSA.neptune.utilitiles.SharedPreferencesMigration
import com.cradleVSA.neptune.utilitiles.UnixTimestamp
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
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
     * @return a [Success] variant if the user was able to login successfully,
     *  otherwise a [Failure] or [NetworkException] will be returned
     */
    suspend fun login(email: String, password: String) = withContext(Dispatchers.Default) {
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

        val patientsJob = launch {
            val startTime = System.currentTimeMillis()

            val mapper = jacksonObjectMapper()

            val channel = Channel<String>()

            val databaseJob = launch {
                for (jsonString in channel) {
                    val patientAndReadings = PatientAndReadings.unmarshal(JSONObject(jsonString))
                    patientManager.addPatientWithReadings(
                        patientAndReadings.patient,
                        patientAndReadings.readings,
                        areReadingsFromServer = true,
                        isPatientNew = true
                    )
                }
            }

            val result = restApi.getAllPatientsStreaming { inputStream ->
                // Parse JSON strings directly from the HTTPUrlConnection's input stream to avoid
                // dealing with a ByteArray of an entire JSON array in memory and trying to convert
                // that into a String.
                mapper.createParser(inputStream).use { parser ->
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        throw IOException("expected JSON array input")
                    }

                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        try {
                            // TODO: Parse patient and readings directly.
                            val jsonString = mapper.readTree<ObjectNode>(parser).toString()
                            channel.send(jsonString)
                        } catch (e: IOException) {
                            Log.e(TAG, "failed to parse JSON object", e)
                            // Propagate exceptions to the Http class so that it can log it
                            throw e
                        }
                    }
                }
            }
            when (result) {
                is Success -> Log.d(TAG, "Patients and readings download successful!")
                is Failure -> Log.e(
                    TAG,
                    "Patient and readings download failed, " +
                        "got status code: ${result.statusCode}"
                )
                is NetworkException -> Log.e(
                    TAG,
                    "Patient and readings download failed, encountered exception",
                    result.cause
                )
            }

            channel.close()
            databaseJob.join()
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Patient/readings download overall took ${endTime - startTime} ms")
        }

        val healthFacilityJob = launch {
            when (val result = restApi.getAllHealthFacilities()) {
                is Success -> {
                    val facilities = result.value
                    if (facilities.isNotEmpty()) {
                        // Select the first health facility by default

                        // TODO: Make it so that the health facility the server sends back cannot
                        //       be removed by the user.
                        // TODO: Show some dialog to select a health facility if the server didn't
                        //        send back one.
                        try {
                            val healthFacilityNameFromServer =
                                loginResult.value.getString("healthFacilityName")

                            facilities.find { it.name == healthFacilityNameFromServer }
                                ?.apply { isUserSelected = true }
                                ?: run {
                                    // Select the first one by default if unable to find it.
                                    facilities[0].isUserSelected = true
                                }
                        } catch (e: JSONException) {
                            // Select the first one by default
                            facilities[0].isUserSelected = true
                        }

                        healthFacilityManager.addAll(facilities)
                    }
                }

                // FIXME: (See above message)
                else -> {
                    Log.e(TAG, "Failed to download health facilities")
                }
            }
        }

        joinAll(patientsJob, healthFacilityJob)

        sharedPreferences.edit(commit = true) {
            putLong(SyncStepperImplementation.LAST_SYNC, loginTime)
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
