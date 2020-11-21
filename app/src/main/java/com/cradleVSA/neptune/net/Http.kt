package com.cradleVSA.neptune.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * HTTP network driver.
 *
 * Provides methods for making generic HTTP requests. The default implementation
 * uses Java's [HttpURLConnection] class to perform the request.
 *
 * When communicating with the CRADLE server, the [com.cradleVSA.neptune.net.RestApi]
 * class should be used instead of this one.
 */
class Http {

    /**
     * Enumeration of common HTTP method request types.
     */
    enum class Method { GET, POST, PUT, DELETE }

    /**
     * Performs a generic HTTP request with custom [InputStream] handling via
     * [inputStreamReader].
     *
     * @param method the request method
     * @param url where to send the request
     * @param headers HTTP headers to include with the request
     * @param doOutput Whether to do output. If this is true, [outputStreamWriter] should be
     * set.
     * @param outputSize The size of the output in bytes if known. Will be used for fixed streaming
     * mode. If size is unknown, then leave it at the default value, which will use chunked
     * streaming. The default value is null.
     * @param timeout the connect and read timeout; default is [DEFAULT_TIMEOUT_MILLIS]
     * @param bufferInput Whether to use a BufferedInputStream for the InputStream. For large or
     * bulk reads like a download of all patients from a server, can be faster to not buffer the
     * input. For smaller reads, recommended to leave this as true. True by default.
     * @param bufferOutput Whether to use a BufferedOutputStream for the OutputStream. For bulk
     * writes like an upload of all new patients to a server, can be faster to not buffer the
     * output. For smaller writes, recommended to leave this as true. True by default.
     * @param outputStreamWriter A lambda that handles the [OutputStream]. This MUST be supplied
     * if [doOutput] is true. Not expected to close the given [OutputStream].
     * @param inputStreamReader A function that processes the [InputStream]. If [requestWithStream]
     * returns a [Success], the value inside of the [Success] will be the return value of
     * the [inputStreamReader]. The [inputStreamReader] is only called if the server returns a
     * successful response code (as defined by [SUCCESS_RANGE]). Not expected to close the given
     * [InputStream]. Note: [IOException]s will be caught by the outer function and it will return a
     * [NetworkException] as the result.
     * @return The result of the network request
     * @throws java.net.MalformedURLException if [url] is malformed
     */
    suspend inline fun <T> requestWithStream(
        method: Method,
        url: String,
        headers: Map<String, String>,
        doOutput: Boolean,
        outputSize: Int? = null,
        timeout: Int = DEFAULT_TIMEOUT_MILLIS,
        bufferInput: Boolean = true,
        bufferOutput: Boolean = true,
        crossinline outputStreamWriter: suspend (OutputStream) -> Unit,
        crossinline inputStreamReader: suspend (InputStream) -> T
    ): NetworkResult<T> = withContext(Dispatchers.IO) {
        // The choice to use inline functions was made to reduce the overhead that
        // occurs with the lambdas. Although we run the risk of increasing the size
        // of generated code, the risk might be acceptable given that there aren't
        // too many places in the app where network requests need to be made.
        //
        // We get "Inappropriate blocking method call" warnings from Android Studio, but should be
        // okay if run in IO Dispatcher.
        with(URL(url).openConnection() as HttpURLConnection) {
            connectTimeout = timeout
            readTimeout = timeout
            requestMethod = method.toString()
            headers.forEach { (k, v) -> addRequestProperty(k, v) }
            setRequestProperty("Accept-Encoding", "gzip")
            doInput = true

            val message = "${method.name} $url"
            try {
                if (doOutput) {
                    setDoOutput(true)
                    // This will handle choosing between fixed-length and chunked streaming modes,
                    // buffering, and getting the outputStreamWriter to write to the OutputStream
                    handleOutputStream(outputSize, bufferOutput, outputStreamWriter)
                }

                if (responseCode in SUCCESS_RANGE) {
                    Log.i(TAG, "$message - Success $responseCode")
                    val returnBody = gzipInputStream(inputStream, bufferInput)
                        .use { inputStreamReader(it) }

                    Success(returnBody, responseCode)
                } else {
                    Log.e(TAG, "$message - Failure $responseCode")
                    // The errorStream can be null (e.g., server sends no error data)
                    val responseBody = errorStream?.let { gzipInputStream(it, bufferInput) }
                        ?.use { it.readBytes() }
                        ?: ByteArray(0)

                    Failure(responseBody, responseCode)
                }
            } catch (ex: IOException) {
                Log.e(TAG, "$message - IOException", ex)
                NetworkException(ex)
            }
        }
    }

    /**
     * Sends a generic HTTP request and expects a JSON response. The JSON response is then parsed
     * by the [inputStreamReader], and then whatever the [inputStreamReader] lambda returns is
     * returned by the function
     *
     * The "Content-Type application/json" header is automatically included
     * in requests sent using this function.
     *
     * For comments on what the parameters are for, see [requestWithStream]
     *
     * @see requestWithStream
     */
    suspend inline fun <T> jsonRequestStream(
        method: Method,
        url: String,
        headers: Map<String, String>,
        doOutput: Boolean,
        outputSize: Int? = null,
        timeout: Int = DEFAULT_TIMEOUT_MILLIS,
        bufferInput: Boolean = true,
        bufferOutput: Boolean = true,
        crossinline outputStreamWriter: suspend (OutputStream) -> Unit,
        crossinline inputStreamReader: suspend (InputStream) -> T
    ): NetworkResult<T> = requestWithStream(
        method = method,
        url = url,
        headers = headers + ("Content-Type" to "application/json"),
        doOutput = doOutput,
        outputSize = outputSize,
        timeout = timeout,
        bufferInput = bufferInput,
        bufferOutput = bufferOutput,
        outputStreamWriter = outputStreamWriter,
        inputStreamReader = inputStreamReader
    )

    companion object {
        const val TAG = "HTTP"

        const val DEFAULT_TIMEOUT_MILLIS = 60 * 1000

        // Refer to https://developer.mozilla.org/en-US/docs/Web/HTTP/Status
        val SUCCESS_RANGE = 200 until 300
    }
}

suspend inline fun HttpURLConnection.handleOutputStream(
    outputSize: Int?,
    bufferOutput: Boolean,
    crossinline outputStreamHandler: suspend (OutputStream) -> Unit
) {
    if (outputSize != null) {
        setFixedLengthStreamingMode(outputSize)
    } else {
        // Use the default chunk length
        setChunkedStreamingMode(0)
    }

    if (bufferOutput) {
        BufferedOutputStream(outputStream).use { outputStreamHandler(it) }
    } else {
        outputStream.use { outputStreamHandler(it) }
    }
}

private const val DEFAULT_GZIP_BUFFER_SIZE = 8 * 1024

/**
 * Returns a [GZIPInputStream] using the given [stream] if [HttpURLConnection.getContentEncoding] is
 * "gzip", otherwise returns [stream]. If [shouldBuffer] is true, the returned [InputStream] will be
 * a [BufferedInputStream].
 *
 * Note: [HttpURLConnection] by design automatically handles gzip for
 * [HttpURLConnection.getInputStream] However, it doesn't seem like [HttpsURLConnection] (even
 * though it extends [HttpURLConnection]) does this. See
 * http://andrewt.com/2014/09/01/android-enabling-gzip-compression-over-https.html or try viewing
 * the network connections in Profiler in Android Studio without this function. So we have to handle
 * this for [HttpsURLConnection]. We obviously can't assume that we will be using plaintext HTTP.
 *
 * Note on BREACH attack (http://www.breachattack.com), a vulnerability involving HTTP compression
 * and TLS: Doesn't seem relevant for the Android app. Also, backend doesn't include any tokens in
 * HTTP responses (other than the user/auth endpoint).
 * https://resources.infosecinstitute.com/topic/the-breach-attack/
 * https://security.stackexchange.com/questions/172581/to-avoid-breach-can-we-use-gzip-on-non-token-responses
 */
fun HttpURLConnection.gzipInputStream(stream: InputStream, shouldBuffer: Boolean): InputStream =
    when {
        "gzip".equals(contentEncoding, ignoreCase = true) -> {
            GZIPInputStream(stream, DEFAULT_GZIP_BUFFER_SIZE)
        }
        else -> {
            stream
        }
    }.let { returnStream -> if (shouldBuffer) BufferedInputStream(returnStream) else returnStream }
