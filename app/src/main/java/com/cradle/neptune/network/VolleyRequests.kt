package com.cradle.neptune.network

import android.content.SharedPreferences
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.GET
import com.android.volley.Request.Method.POST
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.view.LoginActivity
import java.net.ConnectException
import java.net.UnknownHostException
import org.json.JSONArray
import org.json.JSONObject

/**
 * A list of requests type for Volley, Add requests type as needed
 */
class VolleyRequests(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FETCH_PATIENTS_TIMEOUT_MS = 150000
        private const val UNAUTHORIZED = 401
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val CONFLICT = 409

        fun getServerErrorMessage(error: VolleyError): String {
            var message = "Unable to upload to server (network error)"
            when {
                error.cause != null -> {
                    message = when (error.cause) {
                        UnknownHostException::class.java -> {
                            "Unable to resolve server address; check server URL in settings."
                        }
                        ConnectException::class.java -> {
                            "Cannot reach server; check network connection."
                        }
                        else -> {
                            error.cause?.message.toString()
                        }
                    }
                }
                error.networkResponse != null -> {
                    message = when (error.networkResponse.statusCode) {
                        UNAUTHORIZED -> "Server rejected credentials; check they are correct in settings."
                        BAD_REQUEST -> "Server rejected upload request; check server URL in settings."
                        NOT_FOUND -> "Server rejected URL; check server URL in settings."
                        CONFLICT -> "The reading or patient might already exists, check global patients"
                        else -> "Server rejected upload; check server URL in settings." +
                            " Code " + error.networkResponse.statusCode
                    }
                }
            }
            return message
        }
    }

    /**
     * returns a [GET] [JsonObjectRequest] type request
     */
    fun getJsonObjectRequest(
        url: String,
        jsonaBody: JSONObject?,
        callback: (NetworkResult<JSONObject>) -> Unit
    ): JsonObjectRequest {
        val successListener = Response.Listener<JSONObject> { callback(Success(it)) }
        val errorListener = Response.ErrorListener { callback(Failure(it)) }

        return object : JsonObjectRequest(GET, url, jsonaBody, successListener, errorListener) {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String>? {
                return getHttpHeaders()
            }
        }
    }

    /**
     * returns a [POST] [JsonObjectRequest] type request
     */
    fun postJsonObjectRequest(
        url: String,
        jsonBody: JSONObject?,
        callback: (NetworkResult<JSONObject>) -> Unit
    ): JsonObjectRequest {
        val successListener = Response.Listener<JSONObject> { callback(Success(it)) }
        val errorListener = Response.ErrorListener { callback(Failure(it)) }
        return object : JsonObjectRequest(POST, url, jsonBody, successListener, errorListener) {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String>? {
                return getHttpHeaders()
            }
        }
    }

    /**
     * returns a [GET] [JsonArrayRequest] type request
     */
    fun getJsonArrayRequest(
        url: String,
        jsonBody: JSONArray?,
        callback: (NetworkResult<JSONArray>) -> Unit
    ): JsonArrayRequest {
        val successListener = Response.Listener<JSONArray> { callback(Success(it)) }
        val errorListener = Response.ErrorListener { callback(Failure(it)) }

        return object : JsonArrayRequest(GET, url, jsonBody, successListener, errorListener) {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String>? {
                return getHttpHeaders()
            }
        }
    }

    /**
     * returns a [POST] [JsonArrayRequest] type request
     */
    fun postJsonArrayRequest(
        url: String,
        jsonaBody: JSONArray?,
        callback: (NetworkResult<JSONArray>) -> Unit
    ):
        JsonArrayRequest {
        val successListener = Response.Listener<JSONArray> { callback(Success(it)) }
        val errorListener = Response.ErrorListener { callback(Failure(it)) }

        return object : JsonArrayRequest(POST, url, jsonaBody, successListener, errorListener) {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String>? {
                return getHttpHeaders()
            }
        }
    }

    private fun getHttpHeaders(): Map<String, String>? {
        val token = sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
        return mapOf(Pair(LoginActivity.AUTH, "Bearer $token"))
    }

    /**
     * policy to wait longer for requests, incase of poor connection
     */
    fun getRetryPolicy(): DefaultRetryPolicy {
        return DefaultRetryPolicy(
            FETCH_PATIENTS_TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )
    }
}
