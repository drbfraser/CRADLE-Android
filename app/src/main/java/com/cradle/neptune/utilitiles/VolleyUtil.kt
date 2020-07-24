package com.cradle.neptune.utilitiles

import android.content.SharedPreferences
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.cradle.neptune.view.LoginActivity
import java.util.HashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Basic volley requests
 */
class VolleyUtil {

    companion object {

        /**
         * returns [JsonObjectRequest] with headers
         */
        @Suppress("LongParameterList")
        fun makeMeJsonObjectRequest(
            method: Int,
            url: String,
            jsonRequest: JSONObject?,
            successListener: Response.Listener<JSONObject>,
            errorListener: Response.ErrorListener,
            sharedPreferences: SharedPreferences
        ): JsonObjectRequest {

            return object : JsonObjectRequest(method,
                url, jsonRequest,
                successListener, errorListener) {
                /**
                 * Passing some request headers
                 */
                override fun getHeaders(): Map<String, String>? {
                    val headers =
                        HashMap<String, String>()
                    val token =
                        sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
                    headers[LoginActivity.AUTH] = "Bearer $token"
                    return headers
                }
            }
        }

        /**
         * returns [JsonArrayRequest] with headers
         */
        @Suppress("LongParameterList")
        fun makeMeJsonArrayRequest(
            method: Int,
            url: String,
            jsonRequest: JSONArray?,
            successListener: Response.Listener<JSONArray>,
            errorListener: Response.ErrorListener,
            sharedPreferences: SharedPreferences
        ): JsonArrayRequest {

            return object : JsonArrayRequest(method,
                url, jsonRequest,
                successListener, errorListener) {
                /**
                 * Passing some request headers
                 */
                override fun getHeaders(): Map<String, String>? {
                    val headers =
                        HashMap<String, String>()
                    val token =
                        sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
                    headers[LoginActivity.AUTH] = "Bearer $token"
                    return headers
                }
            }
        }
    }
}
