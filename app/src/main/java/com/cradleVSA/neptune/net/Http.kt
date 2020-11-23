package com.cradleVSA.neptune.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.io.InputStream

/**
 * HTTP network driver.
 *
 * Provides methods for making generic HTTP requests. The default implementation
 * uses OkHttp to perform the request.
 *
 * When communicating with the CRADLE server, the [com.cradleVSA.neptune.net.RestApi]
 * class should be used instead of this one.
 */
class Http {

    /**
     * Enumeration of common HTTP method request types.
     */
    enum class Method { GET, POST, PUT, DELETE }

    val client = OkHttpClient()

    /**
     * Performs an HTTP request with custom [InputStream] handling via
     * [inputStreamReader].
     *
     * The return value of the [inputStreamReader] will be returned by the function. For cases where
     * putting an entire response in memory isn't optional (e.g., a large JSON array is given by the
     * server), specify [Unit] as the type parameter, and then have the [inputStreamReader] handle
     * elements one by one from the [InputStream]
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request. Content-Type and Content-Encoding
     * headers are not needed.
     * @param requestBody An optional body to send with the request. If doing a POST or PUT, this
     * should not be null. Use [buildJsonRequestBody] to create a ResponseBody with the correct
     * JSON Content-Type headers applied. This is null by default.
     * @param inputStreamReader A function that processes the [InputStream]. If [requestWithStream]
     * returns a [Success], the value inside of the [Success] will be the return value of
     * the [inputStreamReader]. The [inputStreamReader] is only called if the server returns a
     * successful response code. Not expected to close the given [InputStream]. Note:
     * [IOException]s will be caught by the outer function and it will return a [NetworkException]
     * as the result.
     * @return The result of the network request: [Success] if it succeeds, [Failure] if the server
     * returns a non-successful status code, and [NetworkException] if an [IOException] occurs.
     *
     * @throws IllegalArgumentException - if url is not a valid HTTP or HTTPS URL, or if using an
     * HTTP method that requires a non-null [requestBody] (like POST or PUT).
     */
    suspend inline fun <T> requestWithStream(
        method: Method,
        url: String,
        headers: Map<String, String>,
        requestBody: RequestBody? = null,
        crossinline inputStreamReader: suspend (InputStream) -> T
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        val request = Request.Builder().apply {
            url(url)
            // Note: OkHttp will transparently handle accepting and decoding gzip responses.
            // No need to put a Content-Encoding header here.
            headers.forEach { (name, value) -> addHeader(name, value) }
            // This will throw an unchecked exception if requestBody is null when trying to do
            // HTTP methods that need a body.
            method(method.name, requestBody)
        }.build()

        val message = "${method.name} $url"
        try {
            // "Inappropriate blocking method call" should be fine if we do this in Dispatchers.IO.
            client.newCall(request).execute().use {
                if (it.isSuccessful) {
                    Log.i(TAG, "$message - Success ${it.code}")
                    // The byte stream is closed by the `use` function above.
                    return@use Success(inputStreamReader(it.body!!.byteStream()), it.code)
                } else {
                    Log.i(TAG, "$message - Failure ${it.code}")
                    return@use Failure(it.body!!.bytes(), it.code)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "$message - IOException", e)
            NetworkException(e)
        }
    }

    companion object {
        const val TAG = "HTTP"
    }
}
