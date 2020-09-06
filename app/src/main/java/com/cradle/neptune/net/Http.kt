package com.cradle.neptune.net

import android.util.Log
import com.cradle.neptune.model.Marshal
import java.lang.Exception
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
     * @return the result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     */
    open fun request(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): NetworkResult<ByteArray> =
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method.toString()
            headers.forEach { (k, v) -> addRequestProperty(k, v) }
            doInput = true

            val message = "${method.name} $url"
            try {
                if (body != null) {
                    doOutput = true
                    outputStream.write(body)
                }

                @Suppress("MagicNumber")
                if (responseCode in 200 until 300) {
                    Log.i("HTTP", "$message - Success $responseCode")
                    val responseBody = inputStream.readBytes()
                    inputStream.close()
                    Success(responseBody, responseCode)
                } else {
                    Log.e("HTTP", "$message - Failure $responseCode")
                    val responseBody = errorStream.readBytes()
                    errorStream.close()
                    Failure(responseBody, responseCode)
                }
            } catch (ex: Exception) {
                Log.e("HTTP", "$message - Exception", ex)
                NetworkException(ex)
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
     * @return the result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     * @throws org.json.JSONException if the response body is not JSON
     */
    fun jsonRequest(
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
    fun <Body> jsonRequest(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: Body?
    ): NetworkResult<Json>
        where Body : Marshal<Json> =
        jsonRequest(method, url, headers, body?.marshal())
}
