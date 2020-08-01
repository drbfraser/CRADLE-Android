package com.cradle.neptune.manager

import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.network.Failure
import com.cradle.neptune.network.HttpMethod
import com.cradle.neptune.network.JsonArrayRequest
import com.cradle.neptune.network.JsonObjectRequest
import com.cradle.neptune.network.NetworkRequest
import com.cradle.neptune.network.NetworkSequenceAbort
import com.cradle.neptune.network.Success
import com.cradle.neptune.network.VolleyRequestQueue
import com.cradle.neptune.view.LoginActivity
import javax.inject.Inject

/**
 * Provides methods to send off network requests.
 */
class NetworkManager @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val volleyRequestQueue: VolleyRequestQueue
) {

    /**
     * Enqueues a new request onto the volley request queue.
     *
     * @param request the request to be sent
     */
    fun enqueue(request: NetworkRequest) {
        val bearerToken = sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
        val volleyRequest = request.asVolleyRequest(this, bearerToken)
        if (volleyRequest != null) {
            volleyRequestQueue.addRequest(volleyRequest)
        }
    }

    fun invokeSampleRequests() {
        val url = "http://10.0.2.2:5000"

        val r = JsonObjectRequest(HttpMethod.GET, "$url/api/patients/001/info", null)
            .bind { result ->
                Log.i("NETWORK", "received result $result")
                when (result) {
                    is Failure -> NetworkSequenceAbort()
                    is Success -> {
                        Log.i("NETWORK", result.value.jsonObject.toString(2))
                        JsonArrayRequest(HttpMethod.GET, "$url/api/patients/001/readings", null)
                    }
                }
            }
            .bind {
                result ->
                Log.i("NETWORK", "received result $result")
                NetworkSequenceAbort()
            }

        enqueue(r)
    }
}
