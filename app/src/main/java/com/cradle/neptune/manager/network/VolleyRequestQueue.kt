package com.cradle.neptune.manager.network

import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

class VolleyRequestQueue internal constructor(context: Context) {

    private val requestQueue:RequestQueue = Volley.newRequestQueue(context.applicationContext)

    fun <T>addRequest(request:Request<T>) {
        requestQueue.add(request)
    }
    companion object {
        private var volleyRequestQueue: VolleyRequestQueue? = null

        fun getInstance(context: Context): VolleyRequestQueue?{
            if (volleyRequestQueue == null){
                volleyRequestQueue = VolleyRequestQueue(context)
            }
            return volleyRequestQueue
        }
    }
}