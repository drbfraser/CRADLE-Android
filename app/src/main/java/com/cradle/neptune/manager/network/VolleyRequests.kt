package com.cradle.neptune.manager.network

import android.content.Context
import android.content.SharedPreferences
import com.android.volley.Request.Method.PUT
import com.android.volley.Request.Method.GET
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.utilitiles.VolleyUtil
import com.cradle.neptune.view.LoginActivity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * A list of requests type for Volley
 */
class VolleyRequests(private val sharedPreferences: SharedPreferences)  {

    /**
     * returns a [GET] [JsonObjectRequest] type request
     */
    fun getJsonObjectRequest(url: String, jsonaBody: JSONObject?,
        successListener: Response.Listener<JSONObject>, errorListener: Response.ErrorListener): JsonObjectRequest{
            return object : JsonObjectRequest(GET,url,jsonaBody,successListener,errorListener)
                {
                    /**
                     * Passing some request headers
                     */
                    override fun getHeaders(): Map<String, String>? {
                        return getHttpHeaders()
                    }
                }
    }

    /**
     * returns a [PUT] [JsonObjectRequest] type request
     */
    fun putJsonObjectRequest(url: String, jsonaBody: JSONObject?,
        successListener: Response.Listener<JSONObject>, errorListener: Response.ErrorListener): JsonObjectRequest{
        return object : JsonObjectRequest(PUT,url,jsonaBody,successListener,errorListener)
        {
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
    fun getJsonArrayRequest(url: String, jsonBody: JSONArray?,
        successListener: Response.Listener<JSONArray>, errorListener: Response.ErrorListener): JsonArrayRequest{
        return object : JsonArrayRequest(GET,url,jsonBody,successListener,errorListener)
        {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String>? {
                return getHttpHeaders()
            }
        }
    }

    /**
     * returns a [PUT] [JsonArrayRequest] type request
     */
    fun putJsonArrayRequest(url: String, jsonaBody: JSONArray?,
        successListener: Response.Listener<JSONArray>, errorListener: Response.ErrorListener): JsonArrayRequest{
        return object : JsonArrayRequest(PUT,url,jsonaBody,successListener,errorListener)
        {
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

    interface isSuccessFullCallBack{
        fun isSuccessFull(isSuccessFull:Boolean)
    }
}