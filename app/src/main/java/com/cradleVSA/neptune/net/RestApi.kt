package com.cradleVSA.neptune.net

import android.content.SharedPreferences
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.manager.LoginResponse
import com.cradleVSA.neptune.manager.UrlManager
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.GlobalPatient
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.PatientAndReadings
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.Statistics
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper.createWriter
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import javax.inject.Singleton

/**
 * Provides type-safe methods for interacting with the CRADLE server API.
 *
 * Each method is written as a `suspend` function which is executed using the
 * [IO] dispatcher and returns a [NetworkResult]. In general, a method will
 * return a [Success] variant with the desired return value wrapped inside if
 * the server was able to successfully respond to the request. A [Failure]
 * return value means that the request made it to the server, but the server
 * responded with an error and was not able to complete the request. This
 * happens when the requested resource cannot be found for example. A
 * [NetworkException] return value means that the networking driver ([Http])
 * threw an exception when sending the request or handling the response.
 * A timeout is one such cause of an exception for example.
 */
@Singleton
class RestApi constructor(
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val http: Http
) {
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
     * from the server.
     *
     * For memory efficiency, the [InputStream] from the [HttpURLConnection]
     * is parsed by the [inputStreamWriter]. It's expected that the
     * [Patient]s and [Reading]s parsed are inserted into the database during
     * parsing so that there are not a lot of [Patient] and [Reading] objects
     * stuck in memory. The [inputStreamWriter] isn't expected to close
     * the given [InputStream].
     *
     * Only the patient's managed by this user will be returned in the [InputStream].
     * A patient is considered "managed" if it has an association with the logged-in
     * user. An association is automatically made when a patient is created
     * by this user, or it can be manually made by the [associatePatientToUser]
     * method. To query unmanaged (global) patients, use [searchForPatient]
     * or [getPatient].
     *
     * @return A [Success] if the parsing succeeds, otherwise a [Failure]
     * or [NetworkException] if parsing or the connection fails.
     */
    suspend fun getAllPatientsStreaming(
        inputStreamWriter: suspend (InputStream) -> Unit
    ): NetworkResult<Unit> = withContext(IO) {
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.getAllPatients,
            headers = headers,
            inputStreamReader = inputStreamWriter
        )
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
     * Requests all statistics between two input UNIX timestamps.
     *
     * @param date1 UNIX timestamp of the beginning cutoff
     * @param date2 UNIX timestamp of the end cutoff
     * @return Statistics object for the requested dates
     *
     * TODO: revisit after endpoint is finished and maybe add facility and user id filtering.
     */

    fun getStatisticsBetween(date1: Long, date2: Long): Statistics {
        // TODO: hook this up to the endpoint and do async network IO
        val testData = listOf(
            "{\"patients_referred\":2,\"sent_referrals\":58," +
                "\"days_with_readings\":5,\"unique_patient_readings\":102,\"total_readings\"" +
                ":295,\"color_readings\":{\"GREEN\":116,\"RED_DOWN\":4,\"RED_UP\":58,\"YELLOW_DOWN\"" +
                ":22,\"YELLOW_UP\":95}}",
            "{\"patients_referred\":5,\"sent_referrals\":93," +
                "\"days_with_readings\":8,\"unique_patient_readings\":122,\"total_readings\"" +
                ":285,\"color_readings\":{\"GREEN\":136,\"RED_DOWN\":44,\"RED_UP\":68,\"YELLOW_DOWN\"" +
                ":42,\"YELLOW_UP\":15}}",
            "{\"patients_referred\":35,\"sent_referrals\":9," +
                "\"days_with_readings\":48,\"unique_patient_readings\":192,\"total_readings\"" +
                ":485,\"color_readings\":{\"GREEN\":36,\"RED_DOWN\":424,\"RED_UP\":648,\"YELLOW_DOWN\"" +
                ":22,\"YELLOW_UP\":18}}"
        )
        val index = (0..2).random()
        return JacksonMapper.mapper.readValue(testData[index])
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
     * Sends a request to the server to associate the patient with a given [id]
     * to the currently logged in user.
     *
     * The act of associating a patient with this user tells the server that
     * the patient is "managed" by this user and will show up in responses to
     * the [getAllPatientsStreaming] method.
     *
     * @param id id of the patient to associate
     * @return whether the request was successful or not
     */
    suspend fun associatePatientToUser(id: String): NetworkResult<Unit> =
        withContext(IO) {
            // more efficient to just construct the bytes directly
            val body = "{\"patientId\":\"$id\"}".encodeToByteArray()
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.userPatientAssociation,
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = {},
            ).map { Unit }
        }

    /**
     * Requests a list of all available health facilities from the server.
     *
     * @return a list of health facilities
     */
    suspend fun getAllHealthFacilities(
        inputStreamReader: suspend (InputStream) -> Unit
    ): NetworkResult<Unit> = withContext(IO) {
        http.makeRequest(
            method = Http.Method.GET,
            url = urlManager.healthFacilities,
            headers = headers,
            inputStreamReader = inputStreamReader,
        )
    }

    /**
     * Syncs the patients on the device with the server, where [lastSyncTimestamp] is the last time
     * the patients have been synced with the server. The given [listOfPatients] should be
     * new patients. Sync conflicts are handled by the server.
     *
     * The [inputStreamReader] will read the server's response, which include new and edited
     * patients.
     */
    suspend fun syncPatients(
        listOfPatients: List<Patient>,
        lastSyncTimestamp: Long = 1L,
        inputStreamReader: suspend (InputStream) -> Unit
    ): NetworkResult<Unit> =
        withContext(IO) {
            val body = createWriter<List<Patient>>().writeValueAsBytes(listOfPatients)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.getPatientsSync(lastSyncTimestamp),
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = inputStreamReader
            )
        }

    /**
     * Syncs the readings on the device with the server, where [lastSyncTimestamp] is the last time
     * the patients have been synced with the server. The given [listOfReadings] should be
     * new readings. Sync conflicts are handled by the server.
     *
     * The [inputStreamReader] will read the server's response, which include new and edited
     * readings, referrals, and assessments / follow ups.
     */
    suspend fun syncReadings(
        listOfReadings: List<Reading>,
        lastSyncTimestamp: Long = 1L,
        inputStreamReader: suspend (InputStream) -> Unit
    ): NetworkResult<Unit> =
        withContext(IO) {
            val body = createWriter<List<Reading>>().writeValueAsBytes(listOfReadings)
            http.makeRequest(
                method = Http.Method.POST,
                url = urlManager.getReadingsSync(lastSyncTimestamp),
                headers = headers,
                requestBody = buildJsonRequestBody(body),
                inputStreamReader = inputStreamReader
            )
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
