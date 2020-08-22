package com.cradle.neptune.net

import android.content.SharedPreferences
import com.cradle.neptune.ext.map
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.view.LoginActivity
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides type-safe methods for interacting with the CRADLE server API.
 *
 * Each method is written as a `suspend` function which is executed using the
 * [IO] dispatcher. The class and all it's methods are marked as `open`
 * allowing them to be mocked out for testing if needed.
 */
open class Api @Inject constructor(
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
    open suspend fun authenticate(email: String, password: String): NetworkResult<JSONObject> =
        withContext(IO) {
            val body = JsonObject(mapOf("email" to email, "password" to password))
            http.jsonRequest(
                Http.Method.POST,
                urlManager.authentication,
                mapOf(),
                body
            ).map { it.obj!! }
        }

    open suspend fun getAllPatients(): NetworkResult<List<PatientAndReadings>> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getAllPatients,
                headers,
                null
            ).map {
                it.arr!!.map(
                    JSONArray::getJSONObject,
                    PatientAndReadings.Companion::unmarshal
                )
            }
        }

    open suspend fun getPatient(id: String): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getPatient(id),
                headers,
                null
            ).map { PatientAndReadings.unmarshal(it.obj!!) }
        }

    open suspend fun getPatientInfo(id: String): NetworkResult<Patient> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getPatientInfo(id),
                headers,
                null
            ).map { Patient.unmarshal(it.obj!!) }
        }

    open suspend fun getReading(id: String): NetworkResult<Reading> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.GET,
                urlManager.getReading(id),
                headers,
                null
            ).map { Reading.unmarshal(it.obj!!) }
        }

    open suspend fun postPatient(patient: PatientAndReadings): NetworkResult<PatientAndReadings> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.POST,
                urlManager.postPatient,
                headers,
                JsonObject(patient.marshal())
            ).map { PatientAndReadings.unmarshal(it.obj!!) }
        }

    open suspend fun postReading(reading: Reading): NetworkResult<Reading> =
        withContext(IO) {
            http.jsonRequest(
                Http.Method.POST,
                urlManager.postReading,
                headers,
                JsonObject(reading.marshal())
            ).map { Reading.unmarshal(it.obj!!) }
        }

    open suspend fun associatePatientToUser(id: String): NetworkResult<JSONObject> =
        withContext(IO) {
            val body = JsonObject(mapOf("patientId" to id))
            http.jsonRequest(
                Http.Method.POST,
                urlManager.userPatientAssociation,
                headers,
                body
            ).map { it.obj!! }
        }

    /**
     * The common headers used for most API requests.
     *
     * By design, the [authenticate] method doesn't include these headers in
     * its request.
     */
    protected open val headers: Map<String, String>
        get() {
            val token = sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
            return if (token != null) {
                mapOf("Authorization" to "Bearer $token")
            } else {
                mapOf()
            }
        }
}
