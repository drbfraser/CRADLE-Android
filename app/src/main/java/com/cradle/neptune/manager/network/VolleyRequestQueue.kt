package com.cradle.neptune.manager.network

import android.app.Application
import android.content.Context
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import javax.inject.Singleton

/**
 * a singleton so that we can use one instance of the queue throughout the application
 */
class VolleyRequestQueue (application: Application) {

    private val requestQueue: RequestQueue = Volley.newRequestQueue(application)

    fun <T> addRequest(request: Request<T>) {
        requestQueue.add(request)
    }
}
