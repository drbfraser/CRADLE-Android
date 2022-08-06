package com.cradleplatform.neptune.utilities

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class GzipCompressor {
    companion object {
        fun compress(msg: String): ByteArray {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).bufferedWriter(UTF_8).use { it.write(msg) }
            return bos.toByteArray()
        }

        fun decompress(msgInByteArray: ByteArray): String {
            return GZIPInputStream(msgInByteArray.inputStream()).bufferedReader(UTF_8)
                .use { it.readText() }
        }
    }
}
