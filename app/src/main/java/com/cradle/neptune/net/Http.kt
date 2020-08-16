package com.cradle.neptune.net

import com.cradle.neptune.model.Marshal
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL

/**
 * Contains functions for making generic HTTP requests.
 */
object Http {

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
    fun request(
        method: Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): NetworkResult<ByteArray> =
        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = method.toString()
            headers.forEach { (k, v) -> addRequestProperty(k, v) }
            doInput = true

            try {
                if (body != null) {
                    doOutput = true
                    outputStream.write(body)
                }

                @Suppress("MagicNumber")
                if (responseCode in 200 until 300) {
                    val responseBody = inputStream.readBytes()
                    inputStream.close()
                    Success(responseBody, responseCode)
                } else {
                    val responseBody = errorStream.readBytes()
                    errorStream.close()
                    Failure(responseBody, responseCode)
                }
            } catch (ex: Exception) {
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
