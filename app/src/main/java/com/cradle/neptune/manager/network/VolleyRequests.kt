package com.cradle.neptune.manager.network

import android.content.SharedPreferences
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.GET
import com.android.volley.Request.Method.POST
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.view.LoginActivity
import org.json.JSONArray
import org.json.JSONObject

/**
 * A list of requests type for Volley, Add requests type as needed
 */
class VolleyRequests(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FETCH_PATIENTS_TIMEOUT_MS = 150000
    }
    /**
     * returns a [GET] [JsonObjectRequest] type request
     */
    fun getJsonObjectRequest(url: String, jsonaBody: JSONObject?,callback: (NetworkResult<JSONObject>) -> Unit): JsonObjectRequest {
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
    fun postJsonObjectRequest(url: String, jsonaBody: JSONObject?, callback: (NetworkResult<JSONObject>) -> Unit): JsonObjectRequest {
        val successListener = Response.Listener<JSONObject> { callback(Success(it)) }
        val errorListener = Response.ErrorListener { callback(Failure(it)) }
        return object : JsonObjectRequest(POST, url, jsonaBody, successListener, errorListener) {
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
    fun getJsonArrayRequest(url: String, jsonBody: JSONArray?, callback: (NetworkResult<JSONArray>) -> Unit): JsonArrayRequest {
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
    fun postJsonArrayRequest(url: String, jsonaBody: JSONArray?, callback: (NetworkResult<JSONArray>) -> Unit):
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
        return DefaultRetryPolicy(FETCH_PATIENTS_TIMEOUT_MS, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
    }

}
