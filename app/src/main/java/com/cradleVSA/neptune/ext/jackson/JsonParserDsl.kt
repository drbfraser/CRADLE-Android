package com.cradleVSA.neptune.ext.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectReader
import java.io.IOException

inline fun JsonParser.parseObject(
    throwIfNotObject: Boolean = true,
    block: JsonParser.() -> Unit,
) {
    if (nextToken() != JsonToken.START_OBJECT) {
        if (throwIfNotObject) {
            throw IOException("expected JSON object input")
        }
        skipChildren()
        return
    }

    while (nextToken() != JsonToken.END_OBJECT) {
        block()
    }
}

/**
 * Parses an array of objects of type [T]. The [block] is called on every object in the array
 * that is being parsed. If [throwIfNotArray] is true, then an [IOException] is thrown if not
 * parsing an array.
 *
 * Expected to be used with [JsonParser]s created with [ObjectReader]s.
 *
 * Before calling this function, [JsonParser.currentToken] must be pointed to the token before
 * the [JsonToken.START_ARRAY] token.
 *
 * Example:
 * ```
 * parser.parseObject {
 *     if (currentName == NewSyncUpdateField.PATIENTS.text) {
 *         // Here, currentToken would be JsonToken.FIELD_NAME
 *         parseObjectArray<PatientAndReadings>(reader) {
 *             // do something each item parsed
 *         }
 *     }
 * }
 * ```
 */
inline fun <T> JsonParser.parseObjectArray(
    objectReader: ObjectReader,
    throwIfNotArray: Boolean = true,
    block: (T) -> Unit,
) {
    if (nextToken() != JsonToken.START_ARRAY) {
        if (throwIfNotArray) {
            throw IOException("expected JSON array input")
        }
        skipChildren()
        return
    }
    if (nextToken() == JsonToken.END_ARRAY) return

    objectReader.readValues<T>(this).use { iterator ->
        iterator.forEachJackson { block(it) }
    }
}
