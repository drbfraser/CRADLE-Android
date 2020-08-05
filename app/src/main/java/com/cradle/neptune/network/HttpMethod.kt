package com.cradle.neptune.network

import com.android.volley.Request

/**
 * HTTP method types along with their corresponding Volley code.
 */
enum class HttpMethod(val volleyMethodCode: Int) {
    GET(Request.Method.GET),
    POST(Request.Method.POST),
    PUT(Request.Method.PUT),
    DELETE(Request.Method.DELETE)
}
