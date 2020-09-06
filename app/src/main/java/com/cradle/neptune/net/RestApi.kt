package com.cradle.neptune.net

import android.content.SharedPreferences
import com.cradle.neptune.ext.map
import com.cradle.neptune.manager.LoginManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.Assessment
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.SyncUpdate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
@Suppress("LargeClass")
class RestApi @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val http: Http
) {
    /**
     * Sends a request to the authentication API to log a user in.
     *
     * @param email the user's email
     * @param password the user's password
     * @return if successful, the [JsonObject] that was returned by the server
     *  which contains a bearer token to authenticate the user
     */
    suspend fun authenticate(email: String, password: String): NetworkResult<JSONObject> =
        withContext(IO) {
            val body = JsonObject(mapOf("email" to email, "password" to password))
            http.jsonRequest(
                Http.Method.POST,
                urlManager.authentication,
                mapOf(),
                body
            ).map { it.obj!! }
        }

    /**
     * Requests all patients, and their associated readings, from the server.
     *
     * Only the patient's manged by this user will be returned in the list. A
     * patient is considered "managed" if it has an association with the logged
     * in user. An association is automatically made when a patient is created
     * by this user or can be manually made by the [associatePatientToUser]
     * method. To query unmanaged (global) patients, use [searchForPatient]
     * or [getPatient].
     *
     * @return if successful, a list of all the patients managed by this user
     */
    suspend fun getAllPatients(): NetworkResult<List<PatientAndReadings>> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getAllPatients,
                headers,
                null
            ).map {
                it.arr!!.map(JSONArray::getJSONObject) { json ->
                    val patientAndReadings = PatientAndReadings.unmarshal(json)

                    // Since we are downloading all of these readings, we must
                    // mark them as being uploaded to the server.
                    for (reading in patientAndReadings.readings) {
                        reading.isUploadedToServer = true
                    }
                    patientAndReadings
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
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getPatient(id),
                headers,
                null
            ).map { PatientAndReadings.unmarshal(it.obj!!) }
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
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getPatientInfo(id),
                headers,
                null
            ).map { Patient.unmarshal(it.obj!!) }
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
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getGlobalPatientSearch(searchString),
                headers,
                null
            ).map {
                it.arr!!.map(
                    JSONArray::getJSONObject,
                    GlobalPatient.Companion::unmarshal
                )
            }
        }

    /**
     * Requests a specific reading with a given [id] from the server.
     *
     * @param id id of the reading to request
     * @return the requested reading
     */
    suspend fun getReading(id: String): NetworkResult<Reading> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getReading(id),
                headers,
                null
            ).map { Reading.unmarshal(it.obj!!) }
        }

    /**
     * Requests a specific assessment (aka. followup) from the server.
     *
     * @param id id of the assessment to request
     * @return the requested assessment
     */
    suspend fun getAssessment(id: String): NetworkResult<Assessment> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getAssessmentById(id),
                headers,
                null
            ).map { Assessment.unmarshal(it.obj!!) }
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
     *
     * @param patient the patient to upload
     * @return the server's version of the uploaded patient and readings
     */
    suspend fun postPatient(patient: PatientAndReadings): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.POST,
                urlManager.postPatient,
                headers,
                JsonObject(patient.marshal())
            ).map { PatientAndReadings.unmarshal(it.obj!!) }
        }

    /**
     * Uploads a patient's demographic information with the intent of modifying
     * an existing patient already on the server. To upload a new patient
     * use [postPatient].
     *
     * @param patient the patient to upload
     * @return whether the request was successful or not
     */
    suspend fun putPatient(patient: Patient): NetworkResult<Unit> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.PUT,
                urlManager.getPatientInfoOnly(patient.id),
                headers,
                JsonObject(patient.marshal())
            ).map { Unit }
        }

    /**
     * Uploads a new reading for a patient which already exists on the server.
     *
     * @param reading the reading to upload
     * @return the server's version of the uploaded reading
     */
    suspend fun postReading(reading: Reading): NetworkResult<Reading> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.POST,
                urlManager.postReading,
                headers,
                JsonObject(reading.marshal())
            ).map { Reading.unmarshal(it.obj!!) }
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
    suspend fun associatePatientToUser(id: String): NetworkResult<Unit> =
        withContext(IO) {
            val body = JsonObject(mapOf("patientId" to id))
            http.jsonRequest(
                Http.Method.POST,
                urlManager.userPatientAssociation,
                headers,
                body
            ).map { Unit }
        }

    /**
     * Requests a list of all available health facilities from the server.
     *
     * @return a list of health facilities
     */
    suspend fun getAllHealthFacilities(): NetworkResult<List<HealthFacility>> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.healthFacilities,
                headers,
                null
            ).map {
                it.arr!!.map(
                    JSONArray::getJSONObject,
                    HealthFacility.Companion::unmarshal
                )
            }
        }

    /**
     * Requests an abridged collection of changes made to the entities managed
     * by the logged in user since a given timestamp.
     *
     * This method is called during the first stage of the sync algorithm to
     * determine what patients/readings/assessments need to be downloaded from
     * the server in order to sync the mobile's state with the server's.
     *
     * @param lastSyncTimestamp timestamp of when the device was last synced
     *  with the server
     * @return a collection of ids for new or edited entities that need to be
     *  downloaded from the server
     */
    suspend fun getUpdates(lastSyncTimestamp: Long): NetworkResult<SyncUpdate> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getUpdates(lastSyncTimestamp),
                headers,
                null
            ).map { SyncUpdate.unmarshal(it.obj!!) }
        }

    /**
     * The common headers used for most API requests.
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
