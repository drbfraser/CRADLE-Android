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
import com.cradle.neptune.view.LoginActivity
import javax.inject.Inject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class Api @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val urlManager: UrlManager,
    private val volleyRequestQueue: VolleyRequestQueue
) {

    // *** Temporary ***
    fun invokeSampleRequests() {
        MainScope().launch {
            val result = patientInfo("001")
            Log.i("API", result.toString())
        }
    }

    suspend fun patientInfo(id: String): NetworkResult<Patient> =
        requestObject(HttpMethod.GET, urlManager.patientInfo(id)).map { json ->
            Patient.unmarshal(json)
        }

    private suspend fun requestObject(method: HttpMethod, url: String, payload: JsonObject? = null) =
        suspendCoroutine<NetworkResult<JsonObject>> { cont ->
            val request = makeVolleyObjectRequest(method, url, payload, cont)
            volleyRequestQueue.addRequest(request)
        }

    private suspend fun requestArray(method: HttpMethod, url: String, payload: JsonArray? = null) =
        suspendCoroutine<NetworkResult<JsonArray>> { cont ->
            val request = makeVolleyArrayRequest(method, url, payload, cont)
            volleyRequestQueue.addRequest(request)
        }

    private fun makeVolleyObjectRequest(
        method: HttpMethod,
        url: String,
        payload: JsonObject?,
        cont: Continuation<NetworkResult<JsonObject>>
    ): Request<*> {
        val errorListener = Response.ErrorListener { err -> cont.resume(Failure(err)) }
        val successListener = Response.Listener<JsonObject> { obj -> cont.resume(Success(obj)) }
        return object :
            JsonObjectRequest(method.volleyMethodCode, url, payload, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? = this@Api.headers
        }
    }

    private fun makeVolleyArrayRequest(
        method: HttpMethod,
        url: String,
        payload: JsonArray?,
        cont: Continuation<NetworkResult<JsonArray>>
    ): Request<*> {
        val errorListener = Response.ErrorListener { err -> cont.resume(Failure(err)) }
        val successListener = Response.Listener<JsonArray> { obj -> cont.resume(Success(obj)) }
        return object :
            JsonArrayRequest(method.volleyMethodCode, url, payload, successListener, errorListener) {
            override fun getHeaders(): Map<String, String>? = this@Api.headers
        }
    }

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
