package com.cradle.neptune.network

import android.content.SharedPreferences
import com.android.volley.Request.Method.GET
import com.android.volley.Request.Method.POST
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.manager.LoginManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * A list of requests type for Volley, Add requests type as needed
 */
class VolleyRequests(private val sharedPreferences: SharedPreferences) {

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
        val token = sharedPreferences.getString(LoginManager.TOKEN_KEY, null)
        return mapOf(Pair("Authorization", "Bearer $token"))
    }
}
