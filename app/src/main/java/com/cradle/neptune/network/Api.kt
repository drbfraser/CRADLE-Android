package com.cradle.neptune.network

import android.content.SharedPreferences
import android.util.Log
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.JsonArray
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.map
import com.cradle.neptune.view.LoginActivity
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Provides asynchronous methods for sending strongly typed requests to the
 * various APIs provided by the server.
 */
class Api @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val volleyRequestQueue: VolleyRequestQueue
) {

    /**
     * Gets all patients (and their associated readings) available to this
     * user from the server and returns them as a list.
     *
     * @return a list of patients and their associated readings or an error
     *  if one occurred
     */
    suspend fun getAllPatients(): NetworkResult<List<PatientAndReadings>> =
        requestArray(HttpMethod.GET, urlManager.getAllPatients).map { json ->
            json.map(JsonArray::getJSONObject, PatientAndReadings.Companion::unmarshal)
        }

    /**
     * Gets all information about a single patient from the server. This
     * includes all of the readings for said user.
     *
     * @param id a patient id
     * @return the patient and all its readings or an error if one occurred
     */
    suspend fun getPatient(id: String): NetworkResult<PatientAndReadings> =
        requestObject(HttpMethod.GET, urlManager.getPatient(id)).map { json ->
            PatientAndReadings.unmarshal(json)
        }

    /**
     * Gets just the demographic information for a single patient from the
     * server.
     *
     * @param id a patient id
     * @return the patient or an error if one occurred
     */
    suspend fun getPatientInfo(id: String): NetworkResult<Patient> =
        requestObject(HttpMethod.GET, urlManager.getPatientInfo(id)).map { json ->
            Patient.unmarshal(json)
        }

    /**
     * Gets a single reading from the server.
     *
     * @param id a reading id
     * @return the reading or an error if one occurred
     */
    suspend fun getReading(id: String): NetworkResult<Reading> =
        requestObject(HttpMethod.GET, urlManager.getReading(id)).map { json ->
            Reading.unmarshal(json)
        }

    /**
     * Posts a patient and its associated readings to the server returning the
     * response from the server. The response may contain additional identifiers
     * or other server-generated data for the posted patient.
     *
     * @param patient a patient and associated readings
     * @return the response from the server or an error if one occurred
     */
    suspend fun postPatient(patient: PatientAndReadings): NetworkResult<PatientAndReadings> =
        requestObject(HttpMethod.POST, urlManager.postPatient, patient.marshal()).map { json ->
            PatientAndReadings.unmarshal(json)
        }

    /**
     * Posts a reading to the server returning the response which may contain
     * additional server-generated identifiers or other data.
     *
     * @param reading a reading
     * @return the response form the server or an error if one occurred
     */
    suspend fun postReading(reading: Reading): NetworkResult<Reading> =
        requestObject(HttpMethod.POST, urlManager.postReading, reading.marshal()).map { json ->
            Reading.unmarshal(json)
        }

    /**
     * Sends a generic JSON object request yielding the response body or an
     * error if one occurred.
     *
     * @param method the HTTP method to use for the request
     * @param url the full URL to send the request to
     * @param payload an optional JSON object to include in the body of the request
     * @return the response or an error if one occurred
     */
    private suspend fun requestObject(method: HttpMethod, url: String, payload: JsonObject? = null) =
        suspendCoroutine<NetworkResult<JsonObject>> { cont ->
            Log.i("API", "Sending request: ${method.name} $url")
            val request = makeVolleyObjectRequest(method, url, payload, cont)
            volleyRequestQueue.addRequest(request)
        }

    /**
     * Sends a generic JSON array request yielding the response body or an
     * error if one occurred.
     *
     * @param method the HTTP method to use for the request
     * @param url the full URL to send the request to
     * @param payload an optional JSON array to include in the body of the request
     * @return the response or an error if one occurred
     */
    private suspend fun requestArray(method: HttpMethod, url: String, payload: JsonArray? = null) =
        suspendCoroutine<NetworkResult<JsonArray>> { cont ->
            Log.i("API", "Sending request: ${method.name} $url")
            val request = makeVolleyArrayRequest(method, url, payload, cont)
            volleyRequestQueue.addRequest(request)
        }

    /**
     * Constructs a [JsonObjectRequest] object, passing the given coroutine
     * continuation as the response listener so that the suspended caller
     * can be resumed once a response arrives.
     *
     * @param method the HTTP method to use for the request
     * @param url the full URL to send the request to
     * @param payload an optional JSON object to include in the body of the request
     * @return a [Request] object
     */
    private fun makeVolleyObjectRequest(
        method: HttpMethod,
        url: String,
        payload: JsonObject?,
        cont: Continuation<NetworkResult<JsonObject>>
    ): Request<*> {
        val errorListener = Response.ErrorListener { err ->
            Log.i("API", "Request to $url failed: ${err.message}")
            cont.resume(Failure(err))
        }

        val successListener = Response.Listener<JsonObject> { obj ->
            // TODO: Probably don't want to be logging response bodies in the long run
            Log.i("API", "Response from $url: ${obj.toString(2)}")
            cont.resume(Success(obj))
        }

        return object :
            JsonObjectRequest(method.volleyMethodCode, url, payload, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? = this@Api.headers
        }
    }

    /**
     * Constructs a [JsonArrayRequest] object, passing the given coroutine
     * continuation as the response listener so that the suspended caller
     * can be resumed once a response arrives.
     *
     * @param method the HTTP method to use for the request
     * @param url the full URL to send the request to
     * @param payload an optional JSON array to include in the body of the request
     * @return a [Request] object
     */
    private fun makeVolleyArrayRequest(
        method: HttpMethod,
        url: String,
        payload: JsonArray?,
        cont: Continuation<NetworkResult<JsonArray>>
    ): Request<*> {
        val errorListener = Response.ErrorListener { err ->
            Log.i("API", "Request to $url failed: ${err.message}")
            cont.resume(Failure(err))
        }

        val successListener = Response.Listener<JsonArray> { arr ->
            // TODO: Probably don't want to be logging response bodies in the long run
            Log.i("API", "Response from $url: ${arr.toString(2)}")
            cont.resume(Success(arr))
        }

        return object :
            JsonArrayRequest(method.volleyMethodCode, url, payload, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? = this@Api.headers
        }
    }

    /**
     * The HTTP headers to use in all requests.
     *
     * If a bearer token is found in shared preferences, it will be included in
     * the "Authorization" header.
     */
    private val headers: Map<String, String>?
        get() {
            val bearerToken = sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
            return if (bearerToken != null) {
                mapOf("Authorization" to "Bearer $bearerToken")
            } else {
                null
            }
        }
}
