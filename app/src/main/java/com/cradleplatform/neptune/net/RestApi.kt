package com.cradleplatform.neptune.net

import android.content.SharedPreferences
import android.util.Log
import com.cradleplatform.neptune.ext.jackson.forEachJackson
import com.cradleplatform.neptune.ext.jackson.parseObject
import com.cradleplatform.neptune.ext.jackson.parseObjectArray
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.GestationalAgeMonths
import com.cradleplatform.neptune.model.GlobalPatient
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Statistics
import com.cradleplatform.neptune.sync.PatientSyncField
import com.cradleplatform.neptune.sync.ReadingSyncField
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper.createWriter
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import javax.inject.Singleton

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
@Singleton
class RestApi constructor(
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val http: Http
) {
    companion object {
        private const val TAG = "RestApi"
        private const val UNIT_VALUE_WEEKS = "WEEKS"
        private const val UNIT_VALUE_MONTHS = "MONTHS"
    }

    /**
     * Sends a request to the authentication API to log a user in.
     *
     * @param email the user's email
     * @param password the user's password
     * @return if successful, the [LoginResponse] that was returned by the server
     *  which contains a bearer token to authenticate the user
     */
    suspend fun authenticate(email: String, password: String): NetworkResult<LoginResponse> =
        withContext(IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
                .encodeToByteArray()
            val requestBody = buildJsonRequestBody(body)

            return@withContext http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.authentication,
                headers = mapOf(),
                requestBody = requestBody,
                inputStreamReader = { JacksonMapper.createReader<LoginResponse>().readValue(it) }
            )
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
        patientChannel: SendChannel<Patient>
    ): NetworkResult<Unit> = withContext(IO) {
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.getAllPatients,
            headers = headers,
            inputStreamReader = { inputStream ->
                // Parse JSON strings directly from the input stream to avoid dealing with a
                // ByteArray of an entire JSON array in memory and trying to convert that into a
                // String.
                val reader = JacksonMapper.readerForPatient
                reader.readValues<Patient>(inputStream).use { iterator ->
                    iterator.forEachJackson { patientChannel.send(it) }
                }
            }
        ).also {
            if (it is NetworkResult.Success) {
                patientChannel.close()
            } else {
                patientChannel.close(SyncException("failed to download all associated patients"))
            }
        }
    }

    suspend fun getAllReadings(
        readingChannel: SendChannel<Reading>
    ): NetworkResult<Unit> = withContext(IO) {
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.getAllReadings,
            headers = headers,
            inputStreamReader = { inputStream ->
                val reader = JacksonMapper.readerForReading
                reader.readValues<Reading>(inputStream).use { iterator ->
                    iterator.forEachJackson { readingChannel.send(it) }
                }
            }
        ).also {
            if (it is NetworkResult.Success) {
                readingChannel.close()
            } else {
                readingChannel.close(SyncException("failed to download all associated readings"))
            }
        }
    }

    /**
     * Requests all information (including associated readings) for the patient
     * with a given [id].
     *
     * @param id patient id to get information for
     * @return a patient and its associated readings
     */
    suspend fun getPatient(id: String): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getPatient(id),
                headers = headers,
                inputStreamReader = { JacksonMapper.readerForPatientAndReadings.readValue(it) }
            )
        }

    /**
     * Requests only a patient's demographic data without any of its associated
     * readings. This is usually significantly less data then [getPatient]
     * would return.
     *
     * @param id patient id to get information for
     * @return just the demographic information for a patient
     */
    suspend fun getPatientInfo(id: String): NetworkResult<Patient> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getPatientInfo(id),
                headers = headers,
                inputStreamReader = { JacksonMapper.readerForPatient.readValue(it) }
            )
        }

    /**
     * Searches the server's global patient pool for any patients which names
     * or ids which contain a given [searchString].
     *
     * @param searchString a case-insensitive partial patient name or id to used
     *  as a query
     * @return a list of patients which match the query
     */
    suspend fun searchForPatient(searchString: String): NetworkResult<List<GlobalPatient>> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getGlobalPatientSearch(searchString),
                headers = headers,
                inputStreamReader = {
                    JacksonMapper.createGlobalPatientsListReader().readValue(it)
                }
            )
        }

    /**
     * Requests a specific reading with a given [id] from the server.
     *
     * @param id id of the reading to request
     * @return the requested reading
     */
    suspend fun getReading(id: String): NetworkResult<Reading> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getReading(id),
                headers = headers,
                inputStreamReader = { JacksonMapper.readerForReading.readValue(it) }
            )
        }

    /**
     * Requests a specific assessment (aka. followup) from the server.
     *
     * @param id id of the assessment to request
     * @return the requested assessment
     */
    suspend fun getAssessment(id: String): NetworkResult<Assessment> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getAssessmentById(id),
                headers = headers,
                inputStreamReader = { JacksonMapper.createReader<Assessment>().readValue(it) }
            )
        }

    /**
     * Requests all statistics between two input UNIX timestamps, for a given Facility.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @param filterFacility input health facility to get statistics for
     * @return Statistics object for the requested dates
     */

    suspend fun getStatisticsForFacilityBetween(
        date1: BigInteger,
        date2: BigInteger,
        filterFacility: HealthFacility
    ): NetworkResult<Statistics> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getStatisticsForFacilityBetween(date1, date2, filterFacility.name),
                headers = headers,
                inputStreamReader = { JacksonMapper.mapper.readValue(it) }
            )
        }

    /**
     * Requests all statistics between two input UNIX timestamps, for a given user ID.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @param userID the integer representation of user to get statistics for
     * @return Statistics object for the requested dates
     */

    suspend fun getStatisticsForUserBetween(
        date1: BigInteger,
        date2: BigInteger,
        userID: Int
    ): NetworkResult<Statistics> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getStatisticsForUserBetween(date1, date2, userID),
                headers = headers,
                inputStreamReader = { JacksonMapper.mapper.readValue(it) }
            )
        }

    /**
     * Requests all statistics between two input UNIX timestamps, for all facilities and users.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @return Statistics object for the requested dates
     */

    suspend fun getAllStatisticsBetween(
        date1: BigInteger,
        date2: BigInteger
    ): NetworkResult<Statistics> =
        withContext(IO) {
            http.makeRequest(
                method = Http.Method.GET,
                url = urlManager.getAllStatisticsBetween(date1, date2),
                headers = headers,
                inputStreamReader = { JacksonMapper.mapper.readValue(it) }
            )
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
     * @return the server's version of the uploaded patient and readings
     */
    suspend fun postPatient(patient: PatientAndReadings): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            val body = JacksonMapper.createWriter<PatientAndReadings>().writeValueAsBytes(patient)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.postPatient,
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = { input ->
                    JacksonMapper.readerForPatientAndReadings.readValue(input)
                },
            )
        }

    /**
     * Uploads a patient's demographic information with the intent of modifying
     * an existing patient already on the server. To upload a new patient
     * use [postPatient].
     **
     * @param patient the patient to upload
     * @return whether the request was successful or not
     */
    suspend fun putPatient(patient: Patient): NetworkResult<Unit> =
        withContext(IO) {
            val body = JacksonMapper.writerForPatient.writeValueAsBytes(patient)
            http.makeRequest(
                method = Http.Method.PUT,
                url = urlManager.getPatientInfoOnly(patient.id),
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = {},
            )
        }

    /**
     * Uploads a new drug/medical record for a patient which already exists on the server.
     *
     * @param patient contains the record to be uploaded and the id for the url
     * @param isDrugRecord if it is a drug/medical record
     * @return whether the request was successful or not
     */
    suspend fun postMedicalRecord(patient: Patient, isDrugRecord: Boolean): NetworkResult<Unit> =
        withContext(IO) {
            val jsonObject = JSONObject()
            if (isDrugRecord) {
                jsonObject.put("drugHistory", patient.drugHistory)
            } else {
                jsonObject.put("medicalHistory", patient.medicalHistory)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.postMedicalRecord(patient.id),
                headers = headers,
                requestBody = requestBody,
                inputStreamReader = {},
            )
        }

    /**
     * Uploads a new reading for a patient which already exists on the server.
     *
     * @param reading the reading to upload
     * @return the server's version of the uploaded reading
     */
    suspend fun postReading(reading: Reading): NetworkResult<Reading> =
        withContext(IO) {
            val readingAsBytes = JacksonMapper.writerForReading.writeValueAsBytes(reading)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.postReading,
                headers = headers,
                requestBody = buildJsonRequestBody(readingAsBytes),
                inputStreamReader = { input -> JacksonMapper.readerForReading.readValue(input) },
            )
        }

    /**
     * Sends info for creating a pregnancy record for specific patient
     *
     * @param patient the patient for gestationalAge, and id
     * @return whether the request was successful or not and response from server
     */
    object PregnancyResponse {
        var id: Int? = null
        var gestationalAgeUnit: String? = null
        var lastEdited: Int? = null
        var patientId: String? = null
        var pregnancyEndDate: Int? = null
        var pregnancyOutcome: String? = null
        var pregnancyStartDate: Int? = null
    }
    suspend fun postPregnancy(patient: Patient): NetworkResult<PregnancyResponse> =
        withContext(IO) {
            val jsonObject = JSONObject()

            val units = if (patient.gestationalAge is GestationalAgeMonths) {
                UNIT_VALUE_MONTHS
            } else {
                UNIT_VALUE_WEEKS
            }

            val startDate = patient.gestationalAge?.timestamp.toString()
            jsonObject.put("gestationalAgeUnit", units)
            jsonObject.put("pregnancyStartDate", startDate)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonObject.toString().toRequestBody(mediaType)

            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.postPregnancy(patient.id),
                headers = headers,
                requestBody = requestBody,
                inputStreamReader = { JacksonMapper.mapper.readValue(it) }
            )
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
     * @return whether the request was successful or not
     */
    suspend fun associatePatientToUser(patientId: String): NetworkResult<Unit> =
        withContext(IO) {
            // more efficient to just construct the bytes directly
            val body = "{\"patientId\":\"$patientId\"}".encodeToByteArray()
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.userPatientAssociation,
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = {},
            ).map { }
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
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.healthFacilities,
            headers = headers,
            inputStreamReader = { inputStream ->
                val reader = JacksonMapper.readerForHealthFacility
                reader.readValues<HealthFacility>(inputStream).use { iterator ->
                    iterator.forEachJackson { healthFacilityChannel.send(it) }
                }
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
     * send through [patientChannel]. Note that the server response includes the same patients in
     * [patientsToUpload]; by downloading them again, this is how we eventually set [Patient.lastServerUpdate].
     *
     * Parsed patients are sent through the [patientChannel], with progress reporting done by
     * [reportProgressBlock] (first parameter is number of patients downloaded, second is number
     * of patients in total). The [patientChannel] is closed when patient downloading is complete.
     *
     * [patientChannel] will be failed (see [SendChannel.close]) if [Failure] or
     * [NetworkException] is returned, so using any of the Channels can result in a [SyncException]
     * that should be caught by anything handling the Channels.
     *
     * @sample SyncWorker.syncPatients
     */
    suspend fun syncPatients(
        patientsToUpload: List<Patient>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        patientChannel: SendChannel<Patient>,
        reportProgressBlock: suspend (Int, Int) -> Unit,
    ): NetworkResult<Unit> =
        withContext(IO) {
            val body = createWriter<List<Patient>>().writeValueAsBytes(patientsToUpload)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.getPatientsSync(lastSyncTimestamp),
                headers = headers,
                requestBody = buildJsonRequestBody(body),
            ) { inputStream ->
                val reader = JacksonMapper.readerForPatient
                reader.createParser(inputStream).use { parser ->
                    var totalPatients = 0
                    parser.parseObject {
                        when (currentName) {
                            PatientSyncField.TOTAL.text -> {
                                totalPatients = nextIntValue(0)
                                if (totalPatients == 0) {
                                    // don't bother parsing if there's nothing to parse
                                    return@use
                                }
                            }
                            PatientSyncField.PATIENTS.text -> {
                                var patientsDownloaded = 0
                                parseObjectArray<Patient>(reader) {
                                    patientChannel.send(it)
                                    patientsDownloaded++
                                    reportProgressBlock(patientsDownloaded, totalPatients)
                                }
                                patientChannel.close()
                            }
                            PatientSyncField.FACILITIES.text -> {
                                // TODO: Either have a sync endpoint for new facilities, or
                                //  remove this and just redownload facilities from server.
                                // nextToken()
                                // val tree = readValueAsTree<JsonNode>().toPrettyString()
                            }
                        }
                    }
                }
            }.also {
                if (it is NetworkResult.Success) {
                    // In case we return early, this needs to be closed. These is an idempotent
                    // operation, so it doesn't do anything on Successes where we don't return early
                    patientChannel.close()
                } else {
                    // Fail the channel if not successful (note: idempotent operation).
                    patientChannel.close(SyncException("patient download wasn't done properly"))
                }
            }
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
     * @sample SyncWorker.syncReadings
     */
    suspend fun syncReadings(
        readingsToUpload: List<Reading>,
        lastSyncTimestamp: BigInteger = BigInteger.valueOf(1L),
        readingChannel: SendChannel<Reading>,
        referralChannel: SendChannel<Referral>,
        assessmentChannel: SendChannel<Assessment>,
        reportProgressBlock: suspend (Int, Int) -> Unit,
    ): NetworkResult<Unit> = withContext(IO) {
        val body = createWriter<List<Reading>>().writeValueAsBytes(readingsToUpload)
        http.makeRequest(
            method = Http.Method.POST,
            url = urlManager.getReadingsSync(lastSyncTimestamp),
            headers = headers,
            requestBody = buildJsonRequestBody(body)
        ) { inputStream ->
            Log.d(TAG, "Parsing readings now")

            var numReadingsDownloaded = 0
            var numReferralsDownloaded = 0
            var numAssessmentsDownloaded = 0

            val readerForReading = JacksonMapper.createReader<Reading>()
            val readerForReferral = JacksonMapper.createReader<Referral>()
            val readerForAssessment = JacksonMapper.createReader<Assessment>()

            readerForReading.createParser(inputStream).use { parser ->
                var totalDownloaded = 0
                var totalToDownload = 0
                parser.parseObject {
                    when (currentName) {
                        ReadingSyncField.TOTAL.text -> {
                            totalToDownload = nextIntValue(0)
                            Log.d(TAG, "There are $totalToDownload readings + referrals + followups")
                            if (totalToDownload == 0) {
                                // don't bother parsing if there's nothing to parse
                                return@use
                            }
                        }
                        ReadingSyncField.READINGS.text -> {
                            Log.d(TAG, "Starting to parse readings array")
                            parseObjectArray<Reading>(readerForReading) {
                                readingChannel.send(it)
                                totalDownloaded++
                                numReadingsDownloaded++
                                reportProgressBlock(totalDownloaded, totalToDownload)
                            }
                            readingChannel.close()
                        }
                        ReadingSyncField.NEW_REFERRALS.text -> {
                            Log.d(TAG, "Starting to parse NEW_REFERRALS array")
                            parseObjectArray<Referral>(readerForReferral) {
                                referralChannel.send(it)
                                totalDownloaded++
                                numReferralsDownloaded++
                                reportProgressBlock(totalDownloaded, totalToDownload)
                            }
                            referralChannel.close()
                        }
                        ReadingSyncField.NEW_FOLLOW_UPS.text -> {
                            Log.d(TAG, "Starting to parse NEW_FOLLOW_UPS array")
                            parseObjectArray<Assessment>(readerForAssessment) {
                                assessmentChannel.send(it)
                                totalDownloaded++
                                numAssessmentsDownloaded++
                                reportProgressBlock(totalDownloaded, totalToDownload)
                            }
                            assessmentChannel.close()
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                Log.d(
                    TAG,
                    "DEBUG: Downloaded $numReadingsDownloaded readings, " +
                        "$numReferralsDownloaded referrals and " +
                        "$numAssessmentsDownloaded assessments."
                )
            }
            Unit
        }.also {
            if (it is NetworkResult.Success) {
                // In case we return early, these need to be closed. These are idempotent
                // operations, so it doesn't do anything on Successes where we don't return
                // early.
                readingChannel.close()
                referralChannel.close()
                assessmentChannel.close()
            } else {
                // Fail these channels if not successful (note: idempotent operations)
                readingChannel.close(SyncException("failed to sync readings"))
                referralChannel.close(SyncException("failed to sync referrals"))
                assessmentChannel.close(SyncException("failed to sync assessments"))
            }
        }
    }

    /**
     * The common headers used for most API requests.
     * Note: The [Http] class also sets some headers for encoding.
     *
     * By design, the [authenticate] method doesn't include these headers in
     * its request.
     */
    private val headers: Map<String, String>
        get() {
            val token = sharedPreferences.getString(LoginManager.TOKEN_KEY, null)
            return if (token != null) {
                mapOf("Authorization" to "Bearer $token")
            } else {
                mapOf()
            }
        }
}

class SyncException(message: String) : IOException(message)
