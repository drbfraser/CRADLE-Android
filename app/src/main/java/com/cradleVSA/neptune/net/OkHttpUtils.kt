package com.cradleVSA.neptune.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.OutputStream

val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Builds a [RequestBody] using an OutputStream to write. Since the exact size of the response
 * isn't known, OkHttp will use chunked streaming (via ChunkedSource).
 *
 * Use this if the body to send is too big to store in memory at once.
 */
@Suppress("unused")
inline fun buildJsonRequestBody(crossinline outputStreamWriter: (OutputStream) -> Unit) =
    object : RequestBody() {
        override fun contentType() = JSON_MEDIA_TYPE

        override fun writeTo(sink: BufferedSink) {
            outputStreamWriter(sink.outputStream())
        }
    }

/**
 * Builds a [RequestBody] using the [byteArray]. Can be optimized for fixed streaming since the
 * exact length is available.
 *
 * Use this if the request body is small enough (not some big array or object) to not run into
 * OutOfMemoryErrors.
 */
fun buildJsonRequestBody(byteArray: ByteArray) =
    object : RequestBody() {
        override fun contentType() = JSON_MEDIA_TYPE

        override fun contentLength() = byteArray.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            sink.write(byteArray)
        }
    }
