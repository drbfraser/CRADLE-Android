package com.cradleVSA.neptune.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

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

    @Suppress("MagicNumber")
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30L, TimeUnit.SECONDS)
        .readTimeout(30L, TimeUnit.SECONDS)
        .writeTimeout(30L, TimeUnit.SECONDS)
        .certificatePinner(
            // Setup pins so that it's harder to do man-in-the-middle attacks.
            // We're currently pinning against the leaf certificate's SubjectPublicKeyInfo;
            // and also Let's Encrypt Authority X3's pubkey. This is done to decrease the risk of
            // some attack on a user's TLS sessions, because it's our only method of transport
            // security right now. Connections will be made if any one of the pins match any of the
            // certificates from the server.
            //
            // The pins may need to be updated if something genuinely happens on the server side,
            // like a change in certificate authorities. (Since we're pinning against a public key,
            // certificate renewal won't require changing these pins)
            // See the README for instructions on setting up certificate SPKI pinning.
            CertificatePinner.Builder()
                .add(
                    "cradleplatform.com",
                    // leaf cert: CN=cradleplatform.com
                    "sha256/1qf/G8vZPEXgiM+7UbXmySdt6muYDR4LF34I9MSgAMc=",
                    // Let's Encrypt Authority X3---can remove this if we want more security, but
                    // then the app is forced to update if the server ends up changing / rotating
                    // their key. Note: Certificate renewal doesn't imply a key change.
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
                )
                .add(
                    "staging.cradleplatform.com",
                    // leaf cert: CN=staging.cradleplatform.com
                    "sha256/qJkZm9nsAGRnbHi9h1PQrjl9ndXgTHrItwWZtvcEqu4=",
                    // Let's Encrypt Authority X3
                    "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg="
                )
                .build()
        )
        .build()

    /**
     * Performs an HTTP request with the connection's [InputStream] read by [inputStreamReader],
     * and then returns the result of [inputStreamReader] if the connection and download were
     * successful.
     *
     * The return value of the [inputStreamReader] will be returned by the function. For cases where
     * putting an entire response in memory isn't optional (e.g., a large JSON array is given by the
     * server), specify [Unit] as the type parameter T, and then have the [inputStreamReader] handle
     * elements one by one from the [InputStream].
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request. Content-Type and Content-Encoding
     * headers are not needed.
     * @param requestBody An optional body to send with the request. If doing a POST or PUT, this
     * should not be null. Use [buildJsonRequestBody] to create a ResponseBody with the correct
     * JSON Content-Type headers applied. This is null by default.
     * @param inputStreamReader A function that processes the [InputStream]. If [makeRequest]
     * returns a [Success], the value inside of the [Success] will be the return value of
     * the [inputStreamReader]. The [inputStreamReader] is only called if the server returns a
     * successful response code. Not expected to close the given [InputStream]. Note: [IOException]s
     * will be caught by [makeRequest] and return a [NetworkException] as the result.
     * @return The result of the network request: [Success] if it succeeds, [Failure] if the server
     * returns a non-successful status code, and [NetworkException] if an [IOException] occurs.
     *
     * @throws IllegalArgumentException - if url is not a valid HTTP or HTTPS URL, or if using an
     * HTTP method that requires a non-null [requestBody] (like POST or PUT).
     */
    suspend inline fun <T> makeRequest(
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
                    return@use NetworkResult.Success(inputStreamReader(it.body!!.byteStream()), it.code)
                } else {
                    Log.i(TAG, "$message - Failure ${it.code}")
                    return@use NetworkResult.Failure(it.body!!.bytes(), it.code)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "$message - IOException", e)
            NetworkResult.NetworkException(e)
        }
    }

    companion object {
        const val TAG = "HTTP"
    }
}
