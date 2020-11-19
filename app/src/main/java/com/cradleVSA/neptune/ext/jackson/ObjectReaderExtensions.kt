package com.cradleVSA.neptune.ext.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectReader
import java.io.IOException
import java.io.InputStream

/**
 * Performs parsing from [inputStream] for a JSON array of JSON objects. [block] is invoked
 * repeatedly for each JsonToken from the [inputStream].
 *
 * The given [block] should not close the [JsonParser], and it should advance the stream so that
 * after [block] is invoked, [JsonParser.nextToken] is [JsonToken.START_OBJECT] or
 * [JsonToken.END_ARRAY].
 *
 * @sample com.cradleVSA.neptune.ext.jackson.ObjectReaderExtensionsKtTest.parseJsonArrayFromStream
 * @throws IOException if the [InputStream] doesn't start with a "[" token to signify that it's
 * an array.
 */
inline fun ObjectReader.parseJsonArrayFromStream(
    inputStream: InputStream,
    block: (JsonParser) -> Unit
) {
    createParser(inputStream).use { parser ->
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw IOException("expected JSON array input")
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            block(parser)
        }
    }
}
