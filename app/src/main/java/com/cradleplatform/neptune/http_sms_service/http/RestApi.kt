package com.cradleplatform.neptune.http_sms_service.http

import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.asFlow
import com.cradleplatform.neptune.ext.jackson.forEachJackson
import com.cradleplatform.neptune.ext.jackson.parseObject
import com.cradleplatform.neptune.ext.jackson.parseObjectArray
import com.cradleplatform.neptune.http_sms_service.sms.SMSReceiver
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.SmsTransmissionStates
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.manager.RefreshTokenResponse
import com.cradleplatform.neptune.manager.SmsKey
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.GlobalPatient
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.RelayPhoneNumberResponse
import com.cradleplatform.neptune.model.Statistics
import com.cradleplatform.neptune.sync.workers.AssessmentSyncField
import com.cradleplatform.neptune.sync.workers.PatientSyncField
import com.cradleplatform.neptune.sync.workers.ReadingSyncField
import com.cradleplatform.neptune.sync.workers.ReferralSyncField
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper.createWriter
import com.cradleplatform.neptune.viewmodel.UserViewModel
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Provides type-safe methods for interacting with the CRADLE server API.
 *
 * Each method is written as a `suspend` function which is executed using the
 * [IO] dispatcher and returns a [NetworkResult]. In general, a method will
 * return a [NetworkResult.Success] variant with the desired return value wrapped inside if
 * the server was able to successfully respond to the request. A [NetworkResult.Failure]
 * return value means that the request made it to the server, but the server
 * responded with an error and was not able to complete the request. This
 * happens when the requested resource cannot be found for example. A
 * [NetworkResult.NetworkException] return value means that the networking driver ([Http])
 * threw an exception when sending the request or handling the response.
 * A timeout is one such cause of an exception for example.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class RestApi(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val http: Http,
    private val smsStateReporter: SmsStateReporter,
    private val smsSender: SMSSender,
    private val smsDataProcessor: SMSDataProcessor
) {
    private lateinit var smsReceiver: SMSReceiver

    @OptIn(ExperimentalEncodingApi::class)
    private val base64 = Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    companion object {
        private const val TAG = "RestApi"
        private const val UNIT_VALUE_WEEKS = "WEEKS"
        private const val UNIT_VALUE_MONTHS = "MONTHS"
    }

    private fun setupSmsReceiver() {
        val phoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
            ?: error("Invalid phone number")

        smsReceiver = SMSReceiver(smsSender, phoneNumber, smsStateReporter)

        val intentFilter = IntentFilter()
        intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        intentFilter.priority = Int.MAX_VALUE

        context.registerReceiver(smsReceiver, intentFilter)
    }

    private fun teardownSmsReceiver() {
        context.unregisterReceiver(smsReceiver)
    }

    // TODO: This only handles the case for endpoints that return status code 200 when successful.
    //       Additional logic is necessary for if an endpoint has multiple successful status codes
    //       (e.g. 200 and 201)
    private suspend inline fun <reified T> handleSmsRequest(
        method: Http.Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray = Gson().toJson(JsonObject()).toByteArray(),
    ): NetworkResult<T> {
        val channel = Channel<NetworkResult<T>>()
        setupSmsReceiver()

        try {
            val jsonHeaders = Gson().toJson(headers)
            Log.i("jsonHeaders", jsonHeaders)
            val json = smsDataProcessor.processRequestDataToJSON(
                method, url, Gson().toJson(headers), body
            )
            Log.i("smsDataProcessor", json)
            smsSender.queueRelayContent(json).let { enqueueSuccessful ->
                if (enqueueSuccessful) {
                    smsSender.sendSmsMessage(false)
                }
            }

            smsStateReporter.stateToCollect.asFlow().collect { state ->
                when (state) {
                    SmsTransmissionStates.DONE -> {
                        val response =
                            JacksonMapper.mapper.readValue(smsStateReporter.decryptedMsgLiveData.value,
                                object : TypeReference<T>() {})

                        channel.send(
                            NetworkResult.Success(
                                response, HttpURLConnection.HTTP_OK
                            )
                        )
                    }

                    SmsTransmissionStates.EXCEPTION -> {
                        channel.send(
                            NetworkResult.Failure(
                                smsStateReporter.errorMessageToCollect.value!!.toByteArray(),
                                smsStateReporter.errorCodeToCollect.value!!
                            )
                        )
                    }

                    SmsTransmissionStates.TIME_OUT -> {
                        channel.send(
                            NetworkResult.NetworkException(
                                SMSTimeoutException(
                                    "SMS has timed out"
                                )
                            )
                        )
                    }

                    else -> {}
                }
            }
            return channel.receive()
        } catch (e: IOException) {
            return NetworkResult.NetworkException(e)
        } finally {
            channel.close()
            smsStateReporter.stateToCollect.postValue(SmsTransmissionStates.GETTING_READY_TO_SEND)
            smsStateReporter.errorMessageToCollect.postValue("")
            smsStateReporter.errorCodeToCollect.postValue(0)
            teardownSmsReceiver()
        }
    }

    /**
     * Sends a request to the authentication API to log a user in.
     *
     * @param email The user's email or username.
     * @param password The user's password.
     * @return If successful, the [LoginResponse] that was returned by the server
     *  which contains a bearer token to authenticate the user.
     */
    suspend fun authenticate(
        email: String,
        password: String,
    ): NetworkResult<LoginResponse> = withContext(IO) {
        val body = JSONObject().put("username", email).put("password", password).toString()
            .encodeToByteArray()

        val method = Http.Method.POST
        val url = urlManager.authentication
        val headers = mapOf<String, String>()

        http.makeRequest(method = method,
            url = url,
            headers = headers,
            requestBody = buildJsonRequestBody(body),
            inputStreamReader = {
                Json { ignoreUnknownKeys = true }.decodeFromStream<LoginResponse>(it)
            })
    }

    /**
     *  Sends a request to the /api/user/auth/refresh_token endpoint to refresh the access token.
     *
     * @return If successful, the [RefreshTokenResponse] that was returned by the server
     *  which contains the new access token.
     */
    private suspend fun refreshAccessToken(
        accessToken: String
    ): String? = withContext(IO) {
        val method = Http.Method.POST
        val url = urlManager.refreshToken
        val username = sharedPreferences.getString(UserViewModel.USERNAME, null)

        // Must send username in body of request.
        val body = JSONObject().put("username", username).toString()
            .encodeToByteArray()

        val headers = mapOf("Authorization" to "Bearer $accessToken")

        val result = http.makeRequest(method = method,
            url = url,
            headers = headers,
            requestBody = buildJsonRequestBody(body),

            inputStreamReader = {
                JacksonMapper.createReader<RefreshTokenResponse>().readValue<RefreshTokenResponse>(it)
            })

        if (result is NetworkResult.Success) {
            val newAccessToken = result.value.accessToken
            sharedPreferences.edit(commit = true) {
                putString("accessToken", newAccessToken)
            }
            return@withContext newAccessToken
        } else {
            val errorMessage = result.getStatusMessage(context)
            Log.e(TAG, "Failed to refresh access token:")
            if (errorMessage != null) {
                Log.e(TAG, "$errorMessage")
            }
            return@withContext null
        }
    }

    /**
     * Gets all patients and associated readings for the current user
     * from the server. The parsed results will be sent in the resulting
     * [patientChannel].
     *
     * Only the patient managed by this user will be returned in the [InputStream].
     * A patient is considered "managed" if it has an association with the logged-in
     * user. An association is automatically made when a patient is created
     * by this user, or it can be manually made by the [associatePatientToUser]
     * method. To query unmanaged (global) patients, use [searchForPatient]
     * or [getPatient].
     *
     * [patientChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @return A [Success] if the parsing succeeds, otherwise a [Failure] or [NetworkException] if
     * parsing or the connection fails.
     */
    suspend fun getAllPatients(
        patientChannel: SendChannel<Patient>,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllPatients
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false

                return@withContext http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->
                        // Parse JSON strings directly from the input stream to avoid dealing with a
                        // ByteArray of an entire JSON array in memory and trying to convert that into a
                        // String.
                        try {
                            val reader = JacksonMapper.readerForPatient
                            reader.readValues<Patient>(inputStream).use { iterator ->
                                iterator.forEachJackson {
                                    Log.i(TAG, "$it")
                                    patientChannel.send(it)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    }).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            patientChannel.close(SyncException("failed to parse all associated patients"))
                        } else {
                            patientChannel.close()
                        }
                    } else {
                        patientChannel.close(SyncException("failed to download all associated patients"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Patient>>(
                        method, url, headers
                    )

                    when (networkResult) {
                        is NetworkResult.Success -> {
                            for (patient in networkResult.value) {
                                patientChannel.send(patient)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            patientChannel.close(SyncException("failed to download all associated patients"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            patientChannel.close(SyncException("failed to download all associated patients"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated patients")
                    patientChannel.close(syncException)
                    NetworkResult.NetworkException(syncException)
                }
            }
        }
    }

    suspend fun getAllReadings(
        readingChannel: SendChannel<Reading>,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllReadings
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false

                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->
                        try {
                            val reader = JacksonMapper.readerForReading
                            reader.readValues<Reading>(inputStream).use { iterator ->
                                iterator.forEachJackson { readingChannel.send(it) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    }).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            readingChannel.close(SyncException("failed to parse all associated readings"))
                        } else {
                            readingChannel.close()
                        }
                    } else {
                        readingChannel.close(SyncException("failed to download all associated readings"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Reading>>(
                        method, url, headers
                    )

                    when (networkResult) {
                        is NetworkResult.Success -> {
                            for (reading in networkResult.value) {
                                readingChannel.send(reading)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            readingChannel.close(SyncException("failed to download all associated readings"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            readingChannel.close(SyncException("failed to download all associated readings"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated readings")
                    readingChannel.close(syncException)
                    NetworkResult.NetworkException(syncException)
                }
            }
        }
    }

    suspend fun getAllReferrals(
        referralChannel: SendChannel<Referral>,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllReferrals
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false

                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->
                        try {
                            val reader = JacksonMapper.readerForReferral
                            reader.readValues<Referral>(inputStream).use { iterator ->
                                iterator.forEachJackson { referralChannel.send(it) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    }).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            referralChannel.close(SyncException("failed to parse all associated referrals"))
                        } else {
                            referralChannel.close()
                        }
                    } else {
                        referralChannel.close(SyncException("failed to download all associated referrals"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Referral>>(
                        method, url, headers
                    )

                    when (networkResult) {
                        is NetworkResult.Success -> {
                            for (referral in networkResult.value) {
                                referralChannel.send(referral)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            referralChannel.close(SyncException("failed to download all associated referrals"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            referralChannel.close(SyncException("failed to download all associated referrals"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated referrals")
                    referralChannel.close(syncException)
                    NetworkResult.NetworkException(syncException)
                }
            }
        }
    }

    suspend fun getAllAssessments(
        assessmentChannel: SendChannel<Assessment>,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllAssessments
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false

                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->
                        try {
                            val reader = JacksonMapper.readerForAssessment
                            reader.readValues<Assessment>(inputStream).use { iterator ->
                                iterator.forEachJackson { assessmentChannel.send(it) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    }).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            assessmentChannel.close(SyncException("failed to parse all associated assessments"))
                        } else {
                            assessmentChannel.close()
                        }
                    } else {
                        assessmentChannel.close(SyncException("failed to download all associated assessments"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Assessment>>(
                        method, url, headers
                    )

                    when (networkResult) {
                        is NetworkResult.Success -> {
                            for (assessment in networkResult.value) {
                                assessmentChannel.send(assessment)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            assessmentChannel.close(SyncException("failed to download all associated assessments"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            assessmentChannel.close(SyncException("failed to download all associated assessments"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated assessments")
                    assessmentChannel.close(syncException)
                    NetworkResult.NetworkException(syncException)
                }
            }
        }
    }

    /**
     * Requests all information (including associated readings) for the patient
     * with a given [id].
     *
     * @param id patient id to get information for
     * @param protocol the protocol being used for transmission over the network
     * @return a patient and its associated readings
     */
    suspend fun getPatient(id: String, protocol: Protocol): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            val method = Http.Method.GET
            val url = urlManager.getPatient(id)
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(method = method,
                        url = url,
                        headers = headers,
                        inputStreamReader = { JacksonMapper.readerForPatientAndReadings.readValue(it) })
                }

                Protocol.SMS -> {
                    handleSmsRequest<PatientAndReadings>(
                        method, url, headers
                    )
                }
            }
        }

    /**
     * Requests only a patient's demographic data without any of its associated
     * readings. This is usually significantly less data then [getPatient]
     * would return.
     *
     * @param id patient id to get information for
     * @param protocol the protocol being used for transmission over the network
     * @return just the demographic information for a patient
     */
    suspend fun getPatientInfo(id: String, protocol: Protocol): NetworkResult<Patient> =
        withContext(IO) {
            val method = Http.Method.GET
            val url = urlManager.getPatientInfo(id)
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(method = Http.Method.GET,
                        url = urlManager.getPatientInfo(id),
                        headers = headers,
                        inputStreamReader = { JacksonMapper.readerForPatient.readValue(it) })
                }

                Protocol.SMS -> {
                    handleSmsRequest<Patient>(
                        method, url, headers
                    )
                }
            }
        }

    /**
     * Searches the server's global patient pool for any patients which names
     * or ids which contain a given [searchString].
     *
     * @param searchString a case-insensitive partial patient name or id to used
     *  as a query
     * @param protocol the protocol being used for transmission over the network
     * @return a list of patients which match the query
     */
    suspend fun searchForPatient(
        searchString: String,
        protocol: Protocol
    ): NetworkResult<List<GlobalPatient>> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getGlobalPatientSearch(searchString)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = {
                        JacksonMapper.createGlobalPatientsListReader().readValue(it)
                    })
            }

            Protocol.SMS -> {
                handleSmsRequest<List<GlobalPatient>>(
                    method, url, headers
                )
            }
        }
    }

    /**
     * Requests a specific reading with a given [id] from the server.
     *
     * @param id id of the reading to request
     * @param protocol the protocol being used for transmission over the network
     * @return the requested reading
     */
    suspend fun getReading(id: String, protocol: Protocol): NetworkResult<Reading> =
        withContext(IO) {
            val method = Http.Method.GET
            val url = urlManager.getReading(id)
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(method = Http.Method.GET,
                        url = urlManager.getReading(id),
                        headers = headers,
                        inputStreamReader = { JacksonMapper.readerForReading.readValue(it) })
                }

                Protocol.SMS -> {
                    handleSmsRequest<Reading>(
                        method, url, headers
                    )
                }
            }
        }

    /**
     * Requests a specific assessment (aka. followup) from the server.
     *
     * @param id id of the assessment to request
     * @param protocol the protocol being used for transmission over the network
     * @return the requested assessment
     */
    suspend fun getAssessment(id: String, protocol: Protocol): NetworkResult<Assessment> =
        withContext(IO) {
            val method = Http.Method.GET
            val url = urlManager.getAssessmentById(id)
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(method = method,
                        url = url,
                        headers = headers,
                        inputStreamReader = {
                            JacksonMapper.createReader<Assessment>().readValue(it)
                        })
                }

                Protocol.SMS -> {
                    handleSmsRequest<Assessment>(
                        method, url, headers
                    )
                }
            }
        }

    /**
     * Requests all statistics between two input UNIX timestamps, for a given Facility.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @param filterFacility input health facility to get statistics for
     * @param protocol the protocol being used for transmission over the network
     * @return Statistics object for the requested dates
     */
    suspend fun getStatisticsForFacilityBetween(
        date1: BigInteger,
        date2: BigInteger,
        filterFacility: HealthFacility,
        protocol: Protocol
    ): NetworkResult<Statistics> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getStatisticsForFacilityBetween(date1, date2, filterFacility.name)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { JacksonMapper.mapper.readValue(it) })
            }

            Protocol.SMS -> {
                handleSmsRequest<Statistics>(
                    method, url, headers
                )
            }
        }
    }

    /**
     * Requests all statistics between two input UNIX timestamps, for a given user ID.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @param userID the integer representation of user to get statistics for
     * @param protocol the protocol being used for transmission over the network
     * @return Statistics object for the requested dates
     */
    suspend fun getStatisticsForUserBetween(
        date1: BigInteger,
        date2: BigInteger,
        userID: Int,
        protocol: Protocol
    ): NetworkResult<Statistics> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getStatisticsForUserBetween(date1, date2, userID)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { JacksonMapper.mapper.readValue(it) })
            }

            Protocol.SMS -> {
                handleSmsRequest<Statistics>(
                    method, url, headers
                )
            }
        }
    }

    /**
     * Requests all statistics between two input UNIX timestamps, for all facilities and users.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @param protocol the protocol being used for transmission over the network
     * @return Statistics object for the requested dates
     */

    suspend fun getAllStatisticsBetween(
        date1: BigInteger,
        date2: BigInteger,
        protocol: Protocol
    ): NetworkResult<Statistics> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllStatisticsBetween(date1, date2)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { JacksonMapper.mapper.readValue(it) })
            }

            Protocol.SMS -> {
                handleSmsRequest<Statistics>(
                    method,
                    url,
                    headers,
                )
            }
        }
    }

    /**
     * Uploads a new patient along with associated readings to the server.
     *
     * The server may return a 409-CONFLICT error if a patient or reading
     * exists with the same id as the one being uploaded.
     *
     * If successfully uploaded, the server will respond with its version of
     * the uploaded patient and readings. While most of the data will be the
     * same, the server's version will include some additional properties which
     * were auto-generated by its database so it is better discard the local
     * copy and use the server's version instead when working with this method.
     **
     * @param patient the patient to upload
     * @param protocol the protocol being used for transmission over the network
     * @return the server's version of the uploaded patient and readings
     */
    suspend fun postPatient(
        patient: PatientAndReadings,
        protocol: Protocol
    ): NetworkResult<PatientAndReadings> = withContext(IO) {
        val body = createWriter<PatientAndReadings>().writeValueAsBytes(patient)
        val method = Http.Method.POST
        val url = urlManager.postPatient
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                    inputStreamReader = { input ->
                        JacksonMapper.readerForPatientAndReadings.readValue(input)
                    },
                )
            }

            Protocol.SMS -> {
                handleSmsRequest<PatientAndReadings>(
                    method, url, headers, body
                )
            }
        }
    }

    /**
     * Uploads a new patient along with associated referrals to the server.
     *
     * When uploading a standalone referral, the underlying patient may not exist on the server,
     * in such events, we need to have the ability to upload a referral along with its patient info
     *
     * The server may return a 409-CONFLICT error if a patient or referral
     * exists with the same id as the one being uploaded.
     *
     * If successfully uploaded, the server will respond with its version of
     * the uploaded patient and referrals. While most of the data will be the
     * same, the server's version will include some additional properties which
     * were auto-generated by its database so it is better discard the local
     * copy and use the server's version instead when working with this method.
     **
     * @param patient the patient to upload
     * @param protocol the protocol being used for transmission over the network
     * @return the server's version of the uploaded patient and referrals
     */
    suspend fun postPatient(
        patient: PatientAndReferrals,
        protocol: Protocol
    ): NetworkResult<PatientAndReferrals> = withContext(IO) {
        val body = createWriter<PatientAndReferrals>().writeValueAsBytes(patient)
        val method = Http.Method.POST
        val url = urlManager.postPatient
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                    inputStreamReader = { input ->
                        JacksonMapper.readerForPatientAndReferrals.readValue(input)
                    },
                )
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    /**
     * Uploads form template with user's answers
     **
     * @param mFormTemplate : the form object to upload
     * @param protocol the protocol being used for transmission over the network
     * @return whether the request was successful or not
     */
    suspend fun postFormResponse(
        mFormResponse: FormResponse,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
        val body = gson.toJson(mFormResponse).toByteArray()
        val method = Http.Method.POST
        val url = urlManager.uploadFormResponse
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                    inputStreamReader = {},
                )
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    /**
     * Uploads a patient's demographic information with the intent of modifying
     * an existing patient already on the server. To upload a new patient
     * use [postPatient].
     **
     * @param patient the patient to upload
     * @param protocol the protocol being used for transmission over the network
     * @return whether the request was successful or not
     */
    suspend fun putPatient(patient: Patient, protocol: Protocol): NetworkResult<Unit> =
        withContext(IO) {
            val body = JacksonMapper.writerForPatient.writeValueAsBytes(patient)
            val method = Http.Method.PUT
            val url = urlManager.getPatientInfoOnly(patient.id)
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    return@withContext http.makeRequest(
                        method = method,
                        url = url,
                        headers = headers,
                        requestBody = buildJsonRequestBody(body),
                        inputStreamReader = {},
                    )
                }

                Protocol.SMS -> {
                    return@withContext handleSmsRequest(
                        method, url, headers, body
                    )
                }
            }
        }

    /**
     * Uploads a new drug/medical record for a patient which already exists on the server.
     *
     * @param patient contains the record to be uploaded and the id for the url
     * @param isDrugRecord if it is a drug/medical record
     * @param protocol the protocol being used for transmission over the network
     * @return whether the request was successful or not
     */
    suspend fun postMedicalRecord(
        patient: Patient,
        isDrugRecord: Boolean,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val jsonObject = JSONObject()

        if (isDrugRecord) {
            jsonObject.put("drugHistory", patient.drugHistory)
        } else {
            jsonObject.put("medicalHistory", patient.medicalHistory)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val body = buffer.readByteArray()
        val method = Http.Method.POST
        val url = urlManager.postMedicalRecord(patient.id)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = requestBody,
                    inputStreamReader = {},
                )
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    /**
     * Uploads a new reading for a patient which already exists on the server.
     *
     * @param reading the reading to upload
     * @param protocol the protocol being used for transmission over the network
     * @return the server's version of the uploaded reading
     */
    suspend fun postReading(reading: Reading, protocol: Protocol): NetworkResult<Reading> =
        withContext(IO) {
            val body = JacksonMapper.writerForReading.writeValueAsBytes(reading)
            val method = Http.Method.POST
            val url = urlManager.postReading
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(
                        method = method,
                        url = url,
                        headers = headers,
                        requestBody = buildJsonRequestBody(body),
                        inputStreamReader = { input ->
                            JacksonMapper.readerForReading.readValue(
                                input
                            )
                        },
                    )
                }

                Protocol.SMS -> {
                    handleSmsRequest(
                        method, url, headers, body
                    )
                }
            }
        }

    /**
     * Uploads a new assessment for a patient which already exists on the server.
     *
     * @param assessment the referral to upload
     * @param protocol the protocol being used for transmission over the network
     * @return the server's version of the uploaded assessment
     */
    suspend fun postAssessment(
        assessment: Assessment,
        protocol: Protocol
    ): NetworkResult<Assessment> = withContext(IO) {
        val body = JacksonMapper.writerForAssessment.writeValueAsBytes(assessment)
        val method = Http.Method.POST
        val url = urlManager.postAssessment
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                    inputStreamReader = { input ->
                        JacksonMapper.readerForAssessment.readValue(
                            input
                        )
                    },
                )
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    /**
     * Uploads a new referral for a patient which already exists on the server.
     *
     * @param referral the referral to upload
     * @param protocol the protocol being used for transmission over the network
     * @return the server's version of the uploaded referral
     */
    suspend fun postReferral(referral: Referral, protocol: Protocol): NetworkResult<Referral> =
        withContext(IO) {
            val body = JacksonMapper.writerForReferral.writeValueAsBytes(referral)
            val method = Http.Method.POST
            val url = urlManager.postReferral
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(
                        method = method,
                        url = url,
                        headers = headers,
                        requestBody = buildJsonRequestBody(body),
                        inputStreamReader = { input ->
                            JacksonMapper.readerForReferral.readValue(
                                input
                            )
                        },
                    )
                }

                Protocol.SMS -> {
                    handleSmsRequest(
                        method, url, headers, body
                    )
                }
            }
        }

    object PregnancyResponse {
        var id: Int? = null
        var gestationalAgeUnit: String? = null
        var lastEdited: Int? = null
        var patientId: String? = null
        var pregnancyEndDate: Int? = null
        var pregnancyOutcome: String? = null
        var pregnancyStartDate: Int? = null
    }

    /**
     * Sends info for creating a pregnancy record for specific patient
     * Has the ability to send record with pregnancy start AND end date if full record is being
     * sent to server.
     *
     * @param patient the patient for gestationalAge, and id, and in some cases the end date
     * @param protocol the protocol being used for transmission over the network
     * @return whether the request was successful or not and response from server
     */
    suspend fun postPregnancy(
        patient: Patient,
        protocol: Protocol
    ): NetworkResult<PregnancyResponse> = withContext(IO) {
        val jsonObject = JSONObject()

        val startDate = patient.gestationalAge?.timestamp.toString()

        jsonObject.put("pregnancyStartDate", startDate)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val body = buffer.readByteArray()
        val method = Http.Method.POST
        val url = urlManager.postPregnancy(patient.id)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    requestBody = requestBody,
                    inputStreamReader = { JacksonMapper.mapper.readValue(it) })
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    suspend fun putPregnancy(
        patient: Patient,
        protocol: Protocol
    ): NetworkResult<PregnancyResponse> = withContext(IO) {
        val jsonObject = JSONObject()

        jsonObject.put("pregnancyEndDate", patient.prevPregnancyEndDate.toString())
        jsonObject.put("pregnancyOutcome", patient.prevPregnancyOutcome ?: "")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val body = buffer.readByteArray()
        val method = Http.Method.PUT
        val url = urlManager.putPregnancy(patient.pregnancyId.toString())
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    requestBody = requestBody,
                    inputStreamReader = { JacksonMapper.mapper.readValue(it) })
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    suspend fun postUserPhoneNumber(
        userID: Int,
        phoneNumber: String,
        protocol: Protocol
    ): NetworkResult<Unit> = withContext(IO) {
        val jsonObject = JSONObject()

        jsonObject.put("newPhoneNumber", phoneNumber)
        jsonObject.put("currentPhoneNumber", "")
        jsonObject.put("oldPhoneNumber", "")

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val body = buffer.readByteArray()
        val method = Http.Method.POST
        val url = urlManager.postUserPhoneNumber(userID)
        val headers = makeAuthorizationHeader()

        when (protocol) {
            Protocol.HTTP -> {
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    requestBody = requestBody,
                    inputStreamReader = {})
            }

            Protocol.SMS -> {
                handleSmsRequest(
                    method, url, headers, body
                )
            }
        }
    }

    suspend fun getAllRelayPhoneNumbers(protocol: Protocol): NetworkResult<RelayPhoneNumberResponse> =
        withContext(IO) {
            val method = Http.Method.GET
            val url = urlManager.getAllRelayPhoneNumbers()
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    http.makeRequest(method = method,
                        url = url,
                        headers = headers,
                        inputStreamReader = {
                            JacksonMapper.readerForRelayPhoneNumberResponse.readValue(
                                it
                            )
                        })
                }

                Protocol.SMS -> {
                    handleSmsRequest(
                        method, url, headers
                    )
                }
            }
        }

    suspend fun getCurrentSmsKey(userID: Int): NetworkResult<SmsKey> = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.smsKey(userID)
        val headers = makeAuthorizationHeader()

        http.makeRequest(method = method,
            url = url,
            headers = headers,
            inputStreamReader = { Json.decodeFromStream<SmsKey>(it) })
    }

    suspend fun refreshSmsKey(userID: Int): NetworkResult<SmsKey> = withContext(IO) {
        val jsonObject = JSONObject()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val method = Http.Method.PUT
        val url = urlManager.smsKey(userID)
        val headers = makeAuthorizationHeader()

        http.makeRequest(method = method,
            url = url,
            headers = headers,
            requestBody = requestBody,
            inputStreamReader = { Json.decodeFromStream<SmsKey>(it) })
    }

    suspend fun getNewSmsKey(userID: Int): NetworkResult<SmsKey?> = withContext(IO) {
        val jsonObject = JSONObject()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        val method = Http.Method.POST
        val url = urlManager.smsKey(userID)
        val headers = makeAuthorizationHeader()

        http.makeRequest(method = method,
            url = url,
            headers = headers,
            requestBody = requestBody,
            inputStreamReader = {
                try {
                    Json.decodeFromStream<SmsKey>(it)
                } catch (e: Exception) {
                    null
                }
            })
    }

    /**
     * Sends a request to the server to associate the patient with a given [id]
     * to the currently logged in user.
     *
     * The act of associating a patient with this user tells the server that
     * the patient is "managed" by this user and will show up in responses to
     * the [getAllPatients] method.
     *
     * @param id id of the patient to associate
     * @param protocol the protocol being used for transmission over the network
     * @return whether the request was successful or not
     */
    suspend fun associatePatientToUser(patientId: String, protocol: Protocol): NetworkResult<Unit> =
        withContext(IO) {
            // more efficient to just construct the bytes directly
            val body = "{\"patientId\":\"$patientId\"}".encodeToByteArray()
            val method = Http.Method.POST
            val url = urlManager.userPatientAssociation
            val headers = makeAuthorizationHeader()

            when (protocol) {
                Protocol.HTTP -> {
                    return@withContext http.makeRequest(
                        method = method,
                        url = url,
                        headers = headers,
                        requestBody = buildJsonRequestBody(body),
                        inputStreamReader = {},
                    ).map { }
                }

                Protocol.SMS -> {
                    return@withContext handleSmsRequest(
                        method, url, headers, body
                    )
                }
            }
        }

    /**
     * Requests a list of all available health facilities from the server.
     *
     * [healthFacilityChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     */
    suspend fun getAllHealthFacilities(
        healthFacilityChannel: SendChannel<HealthFacility>
    ): NetworkResult<Unit> = withContext(IO) {
        val headers = makeAuthorizationHeader()
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.healthFacilities,
            headers = headers,
            inputStreamReader = { inputStream ->
                try {
                    val reader = JacksonMapper.readerForHealthFacility
                    reader.readValues<HealthFacility>(inputStream).use { iterator ->
                        iterator.forEachJackson { healthFacilityChannel.send(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                }
                Unit
            },
        ).also {
            if (it is NetworkResult.Success) {
                healthFacilityChannel.close()
            } else {
                healthFacilityChannel.close(SyncException("health facility download failed"))
            }
        }
    }

    /**
     * Syncs the patients on the device with the server, where [lastSyncTimestamp] is the last time
     * the patients have been synced with the server. The given [patientsToUpload] should be
     * new patients. Sync conflicts are handled by the server.
     *
     * The API will first accept our [patientsToUpload], and then the server will respond with new
     * patients between now and [lastSyncTimestamp]. What the server sends back will be parsed and
     * sent through [patientChannel]. Note that the server response includes the same patients in
     * [patientsToUpload]; by downloading them again, this is how we eventually
     * set [Patient.lastServerUpdate].
     *
     * Parsed patients are sent through the [patientChannel], with progress reporting done by
     * [reportProgressBlock] (first parameter is number of patients downloaded, second is number
     * of patients in total). The [patientChannel] is closed when patient downloading is complete.
     *
     * [patientChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @sample SyncAllWorker.syncPatients
     */
    suspend fun syncPatients(
        patientsToUpload: List<Patient>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        patientChannel: SendChannel<Patient>,
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): PatientSyncResult = withContext(IO) {
        val body = createWriter<List<Patient>>().writeValueAsBytes(patientsToUpload)
        val method = Http.Method.POST
        val url = urlManager.getPatientsSync(lastSyncTimestamp)
        val headers = makeAuthorizationHeader()

        var totalPatientsDownloaded = 0
        var errors: String? = null

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                ) { inputStream ->
                    try {
                        val reader = JacksonMapper.readerForPatient
                        reader.createParser(inputStream).use { parser ->
                            parser.parseObject {
                                when (currentName) {
                                    PatientSyncField.PATIENTS.text -> {
                                        parseObjectArray<Patient>(reader) {
                                            patientChannel.send(it)
                                            totalPatientsDownloaded++
                                            reportProgressBlock(
                                                totalPatientsDownloaded, totalPatientsDownloaded
                                            )
                                        }
                                        patientChannel.close()
                                    }

                                    PatientSyncField.ERRORS.text -> {
                                        // TODO: Parse array of objects (refer to issue #62)
                                        nextToken()
                                        errors = readValueAsTree<JsonNode>().toPrettyString()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        Log.i(TAG, e.toString())
                        failedParse = true
                    }
                }.also {
                    if (it is NetworkResult.Success) {
                        // In case we return early, this needs to be closed. These is an idempotent
                        // operation, so it doesn't do anything on Successes where we don't return early
                        if (failedParse) {
                            patientChannel.close(SyncException("patient sync response parsing had failure(s)"))
                        } else {
                            patientChannel.close()
                        }
                    } else {
                        Log.e(TAG, "Patient sync failed:")
                        val errorMessage = it.getStatusMessage(context)
                        if (errorMessage != null) {
                            Log.e(TAG, errorMessage)
                        }
                        // Fail the channel if not successful (note: idempotent operation).
                        patientChannel.close(SyncException("patient download wasn't done properly"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Patient>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (patient in networkResult.value) {
                                totalPatientsDownloaded++
                                patientChannel.send(patient)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            errors = networkResult.body.toString()
                            patientChannel.close(SyncException("failed to download all associated patients"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            errors = networkResult.cause.toString()
                            patientChannel.close(SyncException("failed to download all associated patients"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated patients")
                    errors = e.message

                    patientChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }
        PatientSyncResult(result, patientsToUpload.size, totalPatientsDownloaded, errors)
    }

    /**
     * Syncs the readings on the device with the server, where [lastSyncTimestamp] is the last time
     * the patients have been synced with the server. The given [readingsToUpload] should be
     * new readings, or readings for which the rechecking vitals date should be updated. Sync
     * conflicts are handled by the server.
     *
     * The API will first accept our [readingsToUpload], and then the server will respond with
     * new [Reading]s, [Referral]s, and [Assessment]s between now and [lastSyncTimestamp]. What the
     * server sends back will be parsed and sent through [readingChannel], [referralChannel], and
     * [assessmentChannel]. Note that the server response includes [Reading]s in [readingsToUpload];
     * by downloading them again, that is how we eventually set [Reading.isUploadedToServer].
     *
     * The channels will have parsed information passed into them. Only one channel will be
     * populated at a time, since the parsing is sequential. When finishing parsing one array in the
     * JSON, the current channel will close and the next channel will be the focus. Progress
     * reporting is done by [reportProgressBlock] (first parameter is number of total items
     * downloaded, second is number of items in total; items is the sum of readings, referrals, and
     * assessments).
     *
     * The order of the focusing (and thus closing) is [readingChannel], then [referralChannel],
     * then [assessmentChannel]. (We're following the API response order.)
     *
     * Any of the Channels will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @sample SyncAllWorker.syncReadings
     */
    suspend fun syncReadings(
        readingsToUpload: List<Reading>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        readingChannel: SendChannel<Reading>,
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): ReadingSyncResult = withContext(IO) {
        val body = createWriter<List<Reading>>().writeValueAsBytes(readingsToUpload)
        val method = Http.Method.POST
        val url = urlManager.getReadingsSync(lastSyncTimestamp)
        val headers = makeAuthorizationHeader()

        var totalReadingsDownloaded = 0

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body)
                ) { inputStream ->
                    Log.d(TAG, "Parsing readings now")

                    try {
                        val readerForReading = JacksonMapper.createReader<Reading>()
                        readerForReading.createParser(inputStream).use { parser ->
                            var totalDownloaded = 0
                            parser.parseObject {
                                when (currentName) {
                                    ReadingSyncField.READINGS.text -> {
                                        Log.d(TAG, "Starting to parse readings array")
                                        parseObjectArray<Reading>(readerForReading) {
                                            readingChannel.send(it)
                                            totalDownloaded++
                                            totalReadingsDownloaded++
                                            reportProgressBlock(totalDownloaded, totalDownloaded)
                                        }
                                        readingChannel.close()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        failedParse = true
                    }

                    withContext(Dispatchers.Main) {
                        Log.d(
                            TAG, "DEBUG: Downloaded $totalReadingsDownloaded readings."
                        )
                    }
                    Unit
                }.also {
                    if (it is NetworkResult.Success) {
                        // In case we return early, these need to be closed. These are idempotent
                        // operations, so it doesn't do anything on Successes where we don't return
                        // early.
                        if (failedParse) {
                            readingChannel.close(SyncException("readings sync response parsing had failure(s)"))
                        } else {
                            readingChannel.close()
                        }
                    } else {
                        // Fail these channels if not successful (note: idempotent operations)
                        readingChannel.close(SyncException("failed to sync readings"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Reading>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (reading in networkResult.value) {
                                totalReadingsDownloaded++
                                readingChannel.send(reading)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            readingChannel.close(SyncException("failed to download all associated readings"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated readings")

                    readingChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }

        ReadingSyncResult(
            result, readingsToUpload.size, totalReadingsDownloaded
        )
    }

    /**
     * Syncs the referrals on the device with the server, where [lastSyncTimestamp] is the last time
     * the referrals have been synced with the server. The given [referralsToUpload] should be
     * new referrals. Sync conflicts are handled by the server.
     *
     * The API will first accept our [referralsToUpload], and then the server will respond with new
     * referrals between now and [lastSyncTimestamp]. What the server sends back will be parsed and
     * send through [referralChannel]. Note that the server response includes the same referrals in
     * [referralsToUpload]; by downloading them again, this is how we eventually set [Referral.lastServerUpdate].
     *
     * Parsed referrals are sent through the [referralChannel], with progress reporting done by
     * [reportProgressBlock] (first parameter is number of referrals downloaded, second is number
     * of referrals in total). The [referralChannel] is closed when referral downloading is complete.
     *
     * [referralChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @sample SyncAllWorker.syncReferrals
     */
    suspend fun syncReferrals(
        referralsToUpload: List<Referral>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        referralChannel: SendChannel<Referral>,
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): ReferralSyncResult = withContext(IO) {
        val body = createWriter<List<Referral>>().writeValueAsBytes(referralsToUpload)
        val method = Http.Method.POST
        val url = urlManager.getReferralsSync(lastSyncTimestamp)
        val headers = makeAuthorizationHeader()

        var totalReferralsDownloaded = 0
        var errors: String? = null

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                ) { inputStream ->

                    try {
                        val reader = JacksonMapper.readerForReferral
                        reader.createParser(inputStream).use { parser ->
                            parser.parseObject {
                                when (currentName) {
                                    ReferralSyncField.REFERRALS.text -> {
                                        parseObjectArray<Referral>(reader) {
                                            referralChannel.send(it)
                                            totalReferralsDownloaded++
                                            reportProgressBlock(
                                                totalReferralsDownloaded, totalReferralsDownloaded
                                            )
                                        }
                                        referralChannel.close()
                                    }

                                    ReferralSyncField.ERRORS.text -> {
                                        // TODO: Parse array of objects (refer to issue #62)
                                        nextToken()
                                        errors = readValueAsTree<JsonNode>().toPrettyString()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        failedParse = true
                    }
                }.also {
                    if (it is NetworkResult.Success) {
                        // In case we return early, this needs to be closed. These is an idempotent
                        // operation, so it doesn't do anything on Successes where we don't return early
                        if (failedParse) {
                            referralChannel.close(SyncException("referrals sync response parsing had failure(s)"))
                        } else {
                            referralChannel.close()
                        }
                    } else {
                        // Fail the channel if not successful (note: idempotent operation).
                        referralChannel.close(SyncException("referral download wasn't done properly"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Referral>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (referral in networkResult.value) {
                                totalReferralsDownloaded++
                                referralChannel.send(referral)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            errors = networkResult.body.toString()
                            referralChannel.close(SyncException("failed to download all associated referrals"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            errors = networkResult.cause.toString()
                            referralChannel.close(SyncException("failed to download all associated referrals"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated referrals")
                    errors = e.message

                    referralChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }

        ReferralSyncResult(result, referralsToUpload.size, totalReferralsDownloaded, errors)
    }

    /**
     * Syncs the assessments on the device with the server, where [lastSyncTimestamp] is the last time
     * the assessments have been synced with the server. The given [assessmentsToUpload] should be
     * new assessments. Sync conflicts are handled by the server.
     *
     * The API will first accept our [assessmentsToUpload], and then the server will respond with new
     * assessments between now and [lastSyncTimestamp]. What the server sends back will be parsed and
     * send through [assessmentChannel]. Note that the server response includes the same assessments in
     * [assessmentsToUpload]; by downloading them again, this is how we eventually set [Assessment.lastServerUpdate].
     *
     * Parsed assessments are sent through the [assessmentChannel], with progress reporting done by
     * [reportProgressBlock] (first parameter is number of assessments downloaded, second is number
     * of assessments in total). The [assessmentChannel] is closed when assessment downloading is complete.
     *
     * [assessmentChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @sample SyncAllWorker.syncAssessments
     */
    suspend fun syncAssessments(
        assessmentsToUpload: List<Assessment>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        assessmentChannel: SendChannel<Assessment>,
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): AssessmentSyncResult = withContext(IO) {
        val body = createWriter<List<Assessment>>().writeValueAsBytes(assessmentsToUpload)
        val method = Http.Method.POST
        val url = urlManager.getAssessmentsSync(lastSyncTimestamp)
        val headers = makeAuthorizationHeader()

        var totalAssessmentsDownloaded = 0
        var errors: String? = null

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    requestBody = buildJsonRequestBody(body),
                ) { inputStream ->

                    try {
                        val reader = JacksonMapper.readerForAssessment
                        reader.createParser(inputStream).use { parser ->
                            parser.parseObject {
                                when (currentName) {
                                    AssessmentSyncField.ASSESSMENTS.text -> {
                                        parseObjectArray<Assessment>(reader) {
                                            assessmentChannel.send(it)
                                            totalAssessmentsDownloaded++
                                            reportProgressBlock(
                                                totalAssessmentsDownloaded,
                                                totalAssessmentsDownloaded
                                            )
                                        }
                                        assessmentChannel.close()
                                    }

                                    AssessmentSyncField.ERRORS.text -> {
                                        // TODO: Parse array of objects (refer to issue #62)
                                        nextToken()
                                        errors = readValueAsTree<JsonNode>().toPrettyString()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        failedParse = true
                    }
                }.also {
                    if (it is NetworkResult.Success) {
                        // In case we return early, this needs to be closed. These is an idempotent
                        // operation, so it doesn't do anything on Successes where we don't return early
                        if (failedParse) {
                            assessmentChannel.close(SyncException("assessments sync response parsing had failure(s)"))
                        } else {
                            assessmentChannel.close()
                        }
                    } else {
                        // Fail the channel if not successful (note: idempotent operation).
                        assessmentChannel.close(SyncException("assessment download wasn't done properly"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<Assessment>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (assessment in networkResult.value) {
                                totalAssessmentsDownloaded++
                                assessmentChannel.send(assessment)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            errors = networkResult.body.toString()
                            assessmentChannel.close(SyncException("failed to download all associated assessments"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            errors = networkResult.cause.toString()
                            assessmentChannel.close(SyncException("failed to download all associated assessments"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated assessments")
                    errors = e.message

                    assessmentChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }

        AssessmentSyncResult(
            result, assessmentsToUpload.size, totalAssessmentsDownloaded, errors
        )
    }

    /**
     * Get all and sync Health Facilities from the server with.
     * The parsed results will be sent in the resulting [healthFacilityChannel].
     *
     * [healthFacilityChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @return A [HealthFacilitySyncResult] containing [Success] if the parsing succeeds,
     * otherwise a [Failure] or [NetworkException] if parsing or the connection fail,
     * with a number indicating how many incoming health facilities are successfully parsed
     *
     * TODO: currently lastSyncTimestamp is unused and is waiting for backend to implement
     *  a new endpoint for it. (refer to issue #54)
     */
    suspend fun syncHealthFacilities(
        healthFacilityChannel: SendChannel<HealthFacility>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): HealthFacilitySyncResult = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.healthFacilities
        val headers = makeAuthorizationHeader()

        var totalHealthFacilitiesDownloaded = 0

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(
                    method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->
                        try {
                            val reader = JacksonMapper.readerForHealthFacility
                            reader.readValues<HealthFacility>(inputStream).use { iterator ->
                                iterator.forEachJackson {
                                    healthFacilityChannel.send(it)
                                    totalHealthFacilitiesDownloaded++
                                    reportProgressBlock(
                                        totalHealthFacilitiesDownloaded,
                                        totalHealthFacilitiesDownloaded
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    },
                ).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            healthFacilityChannel.close(
                                SyncException("Failed to parse all Health Facilities during Sync")
                            )
                        } else {
                            healthFacilityChannel.close()
                        }
                    } else {
                        healthFacilityChannel.close(SyncException("health facility download failed"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<HealthFacility>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (healthFacility in networkResult.value) {
                                totalHealthFacilitiesDownloaded++
                                healthFacilityChannel.send(healthFacility)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            healthFacilityChannel.close(
                                SyncException("failed to download all associated health facilities")
                            )
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            healthFacilityChannel.close(
                                SyncException("failed to download all associated health facilities")
                            )
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException = SyncException("failed to parse all associated referrals")

                    healthFacilityChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }

        HealthFacilitySyncResult(result, totalHealthFacilitiesDownloaded)
    }

    /**
     * Get all [FormTemplate]s from the server.
     * The parsed results will be sent in the resulting [formChannel].
     *
     * [formChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @return A [FormSyncResult] containing [Success] if the parsing succeeds,
     * otherwise a [Failure] or [NetworkException] if parsing or the connection fail,
     * with a number [FormSyncResult.totalFormsDownloaded] indicating
     * how many form templates are downloaded in total
     */
    suspend fun getAllFormTemplates(
        formChannel: SendChannel<FormClassification>,
        protocol: Protocol,
        reportProgressBlock: suspend (Int, Int) -> Unit
    ): FormSyncResult = withContext(IO) {
        val method = Http.Method.GET
        val url = urlManager.getAllFormsAsSummary
        val headers = makeAuthorizationHeader()

        var totalClassifications = 0

        val result = when (protocol) {
            Protocol.HTTP -> {
                var failedParse = false
                http.makeRequest(method = method,
                    url = url,
                    headers = headers,
                    inputStreamReader = { inputStream ->

                        try {
                            // Create Gson instance with custom deserializer
                            val customGson = GsonBuilder().registerTypeAdapter(
                                FormClassification::class.java,
                                FormClassification.DeserializerFromFormTemplateStream()
                            ).create()

                            val reader = customGson.newJsonReader(inputStream.bufferedReader())
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val form = customGson.fromJson<FormClassification>(
                                    reader, FormClassification::class.java
                                )
                                formChannel.send(form)
                                totalClassifications++
                                reportProgressBlock(totalClassifications, totalClassifications)
                            }
                            reader.endArray()
                            reader.close()
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            failedParse = true
                        }
                    }).also {
                    if (it is NetworkResult.Success) {
                        if (failedParse) {
                            formChannel.close(SyncException("failed to parse all FormTemplates"))
                        } else {
                            formChannel.close()
                        }
                    } else {
                        formChannel.close(SyncException("failed to download all FormTemplates"))
                    }
                }
            }

            Protocol.SMS -> {
                try {
                    val networkResult = handleSmsRequest<List<FormClassification>>(
                        method, url, headers
                    )

                    val newNetworkResult: NetworkResult<Unit> = when (networkResult) {
                        is NetworkResult.Success -> {
                            for (formClassification in networkResult.value) {
                                totalClassifications++
                                formChannel.send(formClassification)
                            }
                            NetworkResult.Success(
                                Unit, networkResult.statusCode
                            )
                        }

                        is NetworkResult.Failure -> {
                            formChannel.close(SyncException("failed to download all associated form classifications"))
                            NetworkResult.Failure(
                                networkResult.body, networkResult.statusCode
                            )
                        }

                        is NetworkResult.NetworkException -> {
                            formChannel.close(SyncException("failed to download all associated form classifications"))
                            NetworkResult.NetworkException(networkResult.cause)
                        }
                    }

                    newNetworkResult
                } catch (e: Exception) {
                    val syncException =
                        SyncException("failed to parse all associated form classifications")

                    formChannel.close(syncException)

                    NetworkResult.NetworkException(syncException)
                }
            }
        }

        FormSyncResult(result, totalClassifications)
    }

    /**
     * Decodes the payload of the access token JWT and extracts the expiry claim (exp).
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeAccessTokenExpiry(accessToken: String): Long {
        val sections = accessToken.split(".")
        return try {
            val charset = charset("UTF-8")
            val payload = String(base64.decode(sections[1].toByteArray(charset)), charset)
            val payloadJson = JSONObject(payload)
            payloadJson.getLong("exp")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JWT: $e")
            throw e
        }
    }

    /**
     * Decodes the access token and checks if it is expired.
     *
     * @return The access token if it is not expired, or the refreshed access token.
     */
    private suspend fun verifyAccessToken(): String? {
        val accessToken =
            sharedPreferences.getString(UserViewModel.ACCESS_TOKEN_KEY, null) ?: return null
        // Get expiry claim from access token JWT.
        val exp = decodeAccessTokenExpiry(accessToken)

        // Get current timestamp in seconds.
        val currentDateTime = java.util.Date()
        val currentTimestamp: Long = currentDateTime.time / 1000

        // If expiration is more than 5 minutes from now, don't do anything.
        if (exp > currentTimestamp - 300) return accessToken

        // If access token is expired, make a request to the refresh_token endpoint to get a new one
        Log.e(TAG, "ACCESS TOKEN IS EXPIRED!")
        return refreshAccessToken(accessToken)
    }

    /**
     * Creates an Authorization header with the access token. If access token is expired, will try
     * to refresh it.
     */
    private suspend fun makeAuthorizationHeader(): Map<String, String> {
        verifyAccessToken()
        val accessToken = sharedPreferences.getString(UserViewModel.ACCESS_TOKEN_KEY, null)
        return if (accessToken != null) {
            mapOf("Authorization" to "Bearer $accessToken")
        } else {
            mapOf()
        }
    }
}

class SyncException(message: String) : IOException(message)

class SMSTimeoutException(message: String) : IOException(message)
