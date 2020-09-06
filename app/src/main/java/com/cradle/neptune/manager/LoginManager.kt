package com.cradle.neptune.manager

import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.NetworkException
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import com.cradle.neptune.sync.SyncStepperImplementation
import com.cradle.neptune.utilitiles.UnixTimestamp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HTTP_OK = 200

/**
 * Manages logging the user into the server and setting up the application once
 * successfully logged in.
 */
class LoginManager @Inject constructor(
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences,
    private val patientManager: PatientManager,
    private val readingManager: ReadingManager,
    private val healthCentreManager: HealthCentreManager
) {

    companion object {
        const val TOKEN_KEY = "token"
        const val EMAIL_KEY = "loginEmail"
        const val PASSWORD_KEY = "loginPassword"
        const val USER_ID_KEY = "userId"
        const val PASSWORD_SENTINEL = -1
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
    suspend fun login(email: String, password: String) = withContext<NetworkResult<Unit>>(IO) {
        // Send a request to the authentication endpoint to login
        //
        // If we failed to login, return immediately
        when (val result = restApi.authenticate(email, password)) {
            is Success -> {
                val token = result.value.getString("token")
                val userId = result.value.getString("userId")
                with(sharedPreferences.edit()) {
                    putString(TOKEN_KEY, token)
                    putString(USER_ID_KEY, userId)
                    putString(EMAIL_KEY, email)
                    putInt(PASSWORD_KEY, password.hashCode())
                    apply()
                }
            }

            else -> return@withContext result.cast()
        }

        // Once successfully logged in, download the user's patients and health
        // facilities in parallel.

        launch(IO) {
            when (val result = restApi.getAllPatients()) {
                is Success -> {
                    // Set the last sync time to now so that we don't try and
                    // re-download these patients when we go to sync
                    with(sharedPreferences.edit()) {
                        putLong(SyncStepperImplementation.LAST_SYNC, UnixTimestamp.now)
                        apply()
                    }

                    for (patientAndReading in result.value) {
                        patientManager.add(patientAndReading.patient)
                        readingManager.addAllReadings(patientAndReading.readings)
                    }
                }

                // FIXME: Currently the login activity doesn't know what to do
                //  if we fail to download patients. We fail silently here as we
                //  don't want to abort the login just because we couldn't download
                //  patients.
                else -> {
                    Log.e(this::class.simpleName, "Failed to download patients")
                }
            }
        }

        launch(IO) {
            when (val result = restApi.getAllHealthFacilities()) {
                is Success -> {
                    val facilities = result.value
                    if (facilities.isNotEmpty()) {
                        // Select the first health facility by default
                        // TODO: We probably don't want to be automatically doing
                        //  this. It might be better to display a prompt to the
                        //  user or something.
                        facilities[0].isUserSelected = true
                        healthCentreManager.addAll(facilities)
                    }
                }

                // FIXME: (See above message)
                else -> {
                    Log.e(this::class.simpleName, "Failed to download health facilities")
                }
            }
        }

        Success(Unit, HTTP_OK)
    }
}
