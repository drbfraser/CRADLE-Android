package com.cradleplatform.neptune.ext.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectReader
import java.io.IOException

/**
 * Parses a JSON object, calling [block] on each of its fields with [JsonParser.currentToken]
 * pointed to the [JsonToken.FIELD_NAME] token. [block] must read the value of each field it
 * handles. The value of any field it doesn't handle is skipped, so that a new field in a server
 * response doesn't break parsing. If [throwIfNotObject] is true, then an [IOException] is thrown
 * if not parsing an object.
 */
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
        // An unhandled field's value hasn't been read yet. Skip all of it, so that the end of a
        // nested object inside it isn't taken as the end of this object.
        if (currentToken == JsonToken.FIELD_NAME) {
            nextToken()
            skipChildren()
        }
    }
}

/**
 * Parses an array of objects of type [T]. The [onEachObject] is called on every object in the array
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
    onEachObject: (T) -> Unit,
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
        iterator.forEachJackson { onEachObject(it) }
    }
}
