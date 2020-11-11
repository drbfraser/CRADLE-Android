package com.cradle.neptune.net

import android.util.Log
import com.cradle.neptune.model.Marshal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP network driver.
 *
 * Provides methods for making generic HTTP requests. The default implementation
 * uses Java's [HttpURLConnection] class to perform the request. However, this
 * class is marked as `open` meaning that it can the extended to use a different
 * network framework or mocked out for testing.
 *
 * When communicating with the CRADLE server, the [com.cradle.neptune.net.RestApi]
 * class should be used instead of this one.
 */
open class Http {

    /**
     * Enumeration of common HTTP method request types.
     */
    enum class Method { GET, POST, PUT, DELETE }

    /**
     * Performs a generic HTTP request.
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param body an optional body to send along with the request
     * @param timeout the connect and read timeout; defaults to 30 seconds
     * @return the result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     */
    @Deprecated(
        message = """
Use requestWithStream to avoid storing a ByteArray in memory and to avoid creating extra String
objects just to create JSONArrays / JSONObjects for unmarshalling. When using requestWithStream, the
caller is expected to provide code (as the inputStreamHandlerBlock lambda) that directly handles the
InputStream from the HTTPUrlConnection.
            """,
        replaceWith = ReplaceWith(
            "requestWithStream(method, url, headers, body, timeout, inputStreamHandler)"
        )
    )
    open suspend fun request(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeout: Int = DEFAULT_TIMEOUT_MILLIS
    ): NetworkResult<ByteArray> = requestWithStream(
        method = method,
        url = url,
        headers = headers,
        body = body,
        timeout = timeout,
        inputStreamHandler = { inputStream -> inputStream.readBytes() }
    )

    /**
     * Performs a generic HTTP request with custom [InputStream] handling via
     * [inputStreamHandler].
     *
     * TODO: Handle output streaming
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param body an optional body to send along with the request
     * @param timeout the connect and read timeout; defaults to 30 seconds
     * @param inputStreamHandler A lambda that handles the [InputStream]. If [requestWithStream]
     * returns a [Success], the value inside of the [Success] will be the return value of
     * the [inputStreamHandler] lambda. Not expected to close the given [InputStream].
     * @return The result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     */
    suspend inline fun <T> requestWithStream(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
        timeout: Int = DEFAULT_TIMEOUT_MILLIS,
        crossinline inputStreamHandler: suspend (InputStream) -> T
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        // We get "Inappropriate blocking method call" warnings from Android Studio, but should be
        // okay if run in IO Dispatcher.
        with(URL(url).openConnection() as HttpURLConnection) {
            connectTimeout = timeout
            readTimeout = timeout
            requestMethod = method.toString()
            headers.forEach { (k, v) -> addRequestProperty(k, v) }
            doInput = true

            val message = "${method.name} $url"
            try {
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }

                @Suppress("MagicNumber")
                if (responseCode in 200 until 300) {
                    Log.i("HTTP", "$message - Success $responseCode")
                    val returnBody = inputStream.use { inputStreamHandler(it) }
                    Success(returnBody, responseCode)
                } else {
                    Log.e("HTTP", "$message - Failure $responseCode")
                    val responseBody = errorStream.use { it.readBytes() }
                    Failure(responseBody, responseCode)
                }
            } catch (ex: IOException) {
                Log.e("HTTP", "$message - Exception", ex)
                NetworkException(ex)
            }
        }
    }

    /**
     * Sends a generic HTTP request with a JSON body and expects a JSON
     * response.
     *
     * The "Content-Type application/json" header is automatically included
     * in requests sent using this function.
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param body an optional body to send along with the request
     * @return The result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     * @throws org.json.JSONException if the response body is not JSON
     */
    @Deprecated(
        message = """
Use jsonRequestStream to avoid storing a ByteArray in memory and to avoid creating extra String
objects just to create JSONArrays / JSONObjects for unmarshalling. When using jsonRequestStream, the
caller is expected to provide code (as the inputStreamHandlerBlock lambda) that directly handles the
InputStream from the HTTPUrlConnection.
            """,
        replaceWith = ReplaceWith(
            "jsonRequestStream(method, url, headers, body, inputStreamHandler)"
        )
    )
    suspend fun jsonRequest(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: Json?
    ): NetworkResult<Json> =
        request(
            method,
            url,
            headers + ("Content-Type" to "application/json"),
            body?.marshal()
        ).map(Json.Companion::unmarshal)

    /**
     * Sends a generic HTTP request with a JSON body and expects a JSON
     * response. The JSON response is then parsed by the [inputStreamHandler].
     *
     * The "Content-Type application/json" header is automatically included
     * in requests sent using this function.
     *
     * TODO: Handle output streaming
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param body an optional body to send along with the request
     * @param inputStreamHandler A lambda that handles the [InputStream]. If [requestWithStream]
     * returns a [Success], the value inside of the [Success] will be the return value of
     * the [inputStreamHandler] lambda. Not expected to close the given [InputStream].
     * @return the result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     * @throws org.json.JSONException if the response body is not JSON
     */
    suspend inline fun jsonRequestStream(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: Json?,
        crossinline inputStreamHandler: suspend (InputStream) -> Unit
    ): NetworkResult<Unit> =
        requestWithStream(
            method,
            url,
            headers + ("Content-Type" to "application/json"),
            body?.marshal(),
            inputStreamHandler = inputStreamHandler
        )

    /**
     * A generalized version of [jsonRequest] which accepts a generic instance
     * for the [body] parameter.
     *
     * Useful for POST requests where you don't care about the response body.
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param body an optional body to send along with the request
     * @return the result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     * @throws org.json.JSONException if the response body is not JSON
     */
    suspend fun <Body> jsonRequest(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: Body?
    ): NetworkResult<Json>
        where Body : Marshal<Json> = jsonRequest(method, url, headers, body?.marshal())

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60 * 1000
    }
}
