package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.net.SyncException
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.utilities.SharedPreferencesMigration
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
    private val readingManager: ReadingManager,
    private val healthFacilityManager: HealthFacilityManager,
    private val referralManager: ReferralManager,
    private val assessmentManager: AssessmentManager,
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
     * @return a [NetworkResult.Success] variant if the user was able to login successfully,
     *  otherwise a [[NetworkResult.Failure] or [[NetworkResult.NetworkException] will be returned
     */
    suspend fun login(
        email: String,
        password: String,
        parallelDownload: Boolean = true
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
                sharedPreferences.edit(commit = true) {
                    putString(TOKEN_KEY, loginResponse.token)
                    putInt(USER_ID_KEY, loginResponse.userId)
                    putString(EMAIL_KEY, loginResponse.email)
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

            // Once successfully logged in, download the user's patients and health
            // facilities in parallel.
            // We will use this later as the last synced timestamp.
            val loginTime = UnixTimestamp.now

            val patientsDownloadSuccess = downloadPatients() is NetworkResult.Success
            if (patientsDownloadSuccess) {
                sharedPreferences.edit(commit = true) {
                    putString(SyncWorker.LAST_PATIENT_SYNC, loginTime.toString())
                }
            }

            val readingsAsync = async {
                if (patientsDownloadSuccess) {
                    val readingsDownloadSuccess = downloadReadings() is NetworkResult.Success
                    if (readingsDownloadSuccess) {
                        sharedPreferences.edit(commit = true) {
                            putString(SyncWorker.LAST_READING_SYNC, loginTime.toString())
                        }
                    }
                }
            }

            // for unit testing purposes
            if (!parallelDownload) {
                readingsAsync.join()
            }

            // TODO: Maybe make it so that the health facility the server sends back cannot
            //       be removed by the user?
            // TODO: Show some dialog to select a health facility (Refer to issue #24)
            val healthFacilitiesDownloadSuccess = downloadHealthFacilities(loginResult.value.healthFacilityName) is NetworkResult.Success


            val referralsAsync = async {
                if (patientsDownloadSuccess && healthFacilitiesDownloadSuccess) {
                    val referralsDownloadSuccess = downloadReferral() is NetworkResult.Success
                    if (referralsDownloadSuccess) {
                        sharedPreferences.edit(commit = true) {
                            putString(SyncWorker.LAST_REFERRAL_SYNC, loginTime.toString())
                        }
                    }
                }
            }

            val assessmentsAsync = async {
                if (patientsDownloadSuccess) {
                    val assessmentsDownloadSuccess = downloadAssessment() is NetworkResult.Success
                    if (assessmentsDownloadSuccess) {
                        sharedPreferences.edit(commit = true) {
                            putString(SyncWorker.LAST_ASSESSMENT_SYNC, loginTime.toString())
                        }
                    }
                }
            }

            joinAll(readingsAsync, referralsAsync, assessmentsAsync)

            return@withContext NetworkResult.Success(Unit, HTTP_OK)
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

        val channel = Channel<Patient>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (patient in channel) {
                        patientManager.add(patient)
                    }
                    Log.d(TAG, "patient database job is successful")
                }
            } catch (e: SyncException) {
                Log.d(TAG, "patient database job failed")
            }
        }
        val result = restApi.getAllPatients(channel)
        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Patients download overall took ${endTime - startTime} ms")
        return@coroutineScope result
    }

    private suspend fun downloadReadings(): NetworkResult<Unit> = coroutineScope {
        val startTime = System.currentTimeMillis()

        val channel = Channel<Reading>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (reading in channel) {
                        readingManager.addReading(reading, isReadingFromServer = true)
                    }
                    Log.d(TAG, "reading database job is successful")
                }
            } catch (e: SyncException) {
                Log.d(TAG, "reading database job failed")
            }
        }
        val result = restApi.getAllReadings(channel)
        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Readings download overall took ${endTime - startTime} ms")
        return@coroutineScope result
    }

    private suspend fun downloadReferral(): NetworkResult<Unit> = coroutineScope {
        val startTime = System.currentTimeMillis()

        val channel = Channel<Referral>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (referral in channel) {
                        referralManager.addReferral(referral, true)
                    }
                    Log.d(TAG, "referral database job is successful")
                }
            } catch (e: SyncException) {
                Log.d(TAG, "referral database job failed")
            }
        }
        val result = restApi.getAllReferrals(channel)
        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Referral download overall took ${endTime - startTime} ms")
        return@coroutineScope result
    }

    private suspend fun downloadAssessment(): NetworkResult<Unit> = coroutineScope {
        val startTime = System.currentTimeMillis()

        val channel = Channel<Assessment>()
        val databaseJob = launch {
            try {
                database.withTransaction {
                    for (assessment in channel) {
                        assessmentManager.addAssessment(assessment, true)
                    }
                    Log.d(TAG, "assessment database job is successful")
                }
            } catch (e: SyncException) {
                Log.d(TAG, "assessment database job failed")
            }
        }
        val result = restApi.getAllAssessments(channel)
        closeOrCancelChannelByResult(result, channel)
        databaseJob.join()

        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Assessment download overall took ${endTime - startTime} ms")
        return@coroutineScope result
    }

    /**
     * Close the channel for type [T] if the [result] is a [NetworkResult.Success], otherwise cancels it.
     * Note: [RestApi] already handles channel closing, so it's likely this function isn't really
     * useful.
     */
    private inline fun <reified T> closeOrCancelChannelByResult(
        result: NetworkResult<Unit>,
        channel: Channel<T>
    ) {
        val prefix = T::class.java.simpleName
        when (result) {
            is NetworkResult.Success -> {
                channel.close()
                Log.d(TAG, "$prefix download successful!")
            }
            is NetworkResult.Failure -> {
                channel.cancel()
                Log.e(TAG, "$prefix download failed; status code: ${result.statusCode}")
            }
            is NetworkResult.NetworkException -> {
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
    val userId: Int,
    @JsonProperty
    val token: String
)
