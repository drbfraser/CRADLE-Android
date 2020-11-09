package com.cradle.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.R
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.NetworkException
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.utilitiles.UnixTimestamp
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
import javax.inject.Inject

private const val HTTP_OK = 200

/**
 * Manages logging the user into the server and setting up the application once
 * successfully logged in.
 */
class LoginManager @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences,
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

                with(sharedPreferences.edit()) {
                    putString(TOKEN_KEY, token)
                    putInt(USER_ID_KEY, userId)
                    putString(EMAIL_KEY, email)
                    putString(context.getString(R.string.key_vht_name), name)
                    apply()
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
                        areReadingsFromServer = true
                    )
                }
            }

            val result = restApi.getAllPatientsStreaming { inputStream ->
                // Parse JSON strings directly from the HTTPUrlConnection's input stream to avoid
                // dealing with a ByteArray of an entire JSON array in memory and trying to convert
                // that into a String.
                mapper.createParser(inputStream).use { parser ->
                    if (parser.nextToken() != JsonToken.START_ARRAY) {
                        throw IOException()
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
            if (result !is Success) {
                Log.e(TAG, "Failed to download patients")
            }

            channel.close()
            databaseJob.join()
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Patient download and insertion took ${endTime - startTime} ms")
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

        with(sharedPreferences.edit()) {
            putLong(SyncStepperImplementation.LAST_SYNC, loginTime)
            apply()
        }

        Success(Unit, HTTP_OK)
    }
}
