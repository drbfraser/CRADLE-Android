package com.cradle.neptune.model

import com.cradle.neptune.utilitiles.DateUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

/**
 * An alias of [JSONObject] with a name consistent with other class names.
 */
typealias JsonObject = JSONObject

/**
 * An alias of [JSONArray] with a name consistent with other class names.
 */
typealias JsonArray = JSONArray

/**
 * An alias of [JSONException] with a name consistent with other class names.
 */
typealias JsonException = JSONException

/**
 * Describes types which may be used as fields for retrieving and storing
 * values in a [JsonObject].
 *
 * The common use case for this interface is using an enumeration to represent
 * JSON fields instead of having to use strings all the time which are prone
 * to errors.
 */
interface Field {
    /**
     * The textual name of the field that this object represents.
     *
     * Ideally this would be called `name`, but, since enums already have a
     * final field called `name` we can't use that.
     */
    val text: String

    companion object {
        /**
         * Returns an anonymous [Field] object with a [text] field equal to
         * [string].
         *
         * This method is intended for testing purposes or one-off uses of
         * [JsonObject]'s `field` methods. It is strongly encouraged to define
         * an enumeration which implements this interface instead of using this
         * method.
         */
        fun fromString(string: String): Field = object : Field {
            override val text = string
        }
    }
}

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: Int?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: Long?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: Boolean?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: String?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: Double?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * We prefer to store [ZonedDateTime] values as the number of seconds since
 * epoch. However, for legacy support, the inverse getter method supports both
 * unmarshalling epoch seconds as well as formatted strings.
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: ZonedDateTime?): JsonObject {
    val epochSeconds = value?.toEpochSecond()
    return put(field, epochSeconds)
}

/**
 * Maps [field] to [value].
 *
 * [value] will be stored as a sub-object under the field name. For inlining an
 * object (i.e., merging fields) see [union].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: JsonObject?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` the the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: JsonArray?): JsonObject = put(field.text, value)

/**
 * Maps [field] to [value] by first converting [value] into a [JsonArray].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JsonObject.put(field: F, value: List<String>?): JsonObject = put(field, value?.let { JsonArray(it) })

/**
 * Marshals [value] to JSON then stores it as a sub-object under the field name.
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field, M : Marshal<JsonObject>> JsonObject.put(field: F, value: M?): JsonObject =
    put(field.text, value?.marshal())

/**
 * Returns the string value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.stringField(field: F): String = getString(field.text)

/**
 * Returns the string value for the specified field or `null` if it does not
 * exist.
 *
 * @return The string value contained in the field.
 */
fun <F : Field> JsonObject.optStringField(field: F): String? = try {
    val value = getString(field.text)

    // Sometimes, we'll use the string "null" to mean a real `null` and no the
    // text "null" (yes very confusing, I know). To compensate for this, when
    // parsing an optional string, the text "null" will be treated as a literal
    // `null` and not the string "null". Note that this is not the case for the
    // non-optional variant of this method.
    if (value == "null") {
        null
    } else {
        value
    }
} catch (e: JsonException) {
    null
}

/**
 * Returns the integer value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.intField(field: F): Int = getInt(field.text)

/**
 * Returns the integer value for the specified field or `null` if it does not
 * exist.
 *
 * @return The boolean value contained in the field.
 */
fun <F : Field> JsonObject.optIntField(field: F): Int? = try {
    getInt(field.text)
} catch (e: JsonException) {
    null
}

/**
 * Returns the long value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.longField(field: F): Long = getLong(field.text)

/**
 * Returns the long value for the specified field or `null` if it does not
 * exist.
 *
 * @return The long value contained in the field.
 */
fun <F : Field> JsonObject.optLongField(field: F): Long? = try {
    getLong(field.text)
} catch (e: JsonException) {
    null
}

/**
 * Returns the double value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.doubleField(field: F): Double = getDouble(field.text)

/**
 * Returns the double value for the specified field or `null` if it does not
 * exist.
 *
 * @return The double value contained in the field.
 */
fun <F : Field> JsonObject.optDoubleField(field: F): Double? = try {
    getDouble(field.text)
} catch (e: JsonException) {
    null
}

/**
 * Returns the boolean value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.booleanField(field: F): Boolean = getBoolean(field.text)

/**
 * Returns the boolean value for the specified field or `null` if it does not
 * exist.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.optBooleanField(field: F): Boolean? = try {
    getBoolean(field.text)
} catch (e: JsonException) {
    null
}

/**
 * Returns the date value for the specified field.
 *
 * The date may either be encoded in JSON as a long containing the number of
 * seconds since the epoch or a formatted string.
 *
 * @throws JsonException If not such field exists.
 */
fun <F : Field> JsonObject.dateField(field: F): ZonedDateTime {
    // Try epoch seconds first.
    val date = optLongField(field)?.let { DateUtil.getZoneTimeFromLong(it) }
    if (date != null) {
        return date
    }

    // If not epoch seconds then try formatted string.
    //
    // We try and parse using the system's default zoned id. This may cause
    // problems if the string is encoded using a different zone id hence the
    // move to storing dates as seconds from epoch.
    val dateString = stringField(field)
    return try {
        ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.systemDefault()))
    } catch (e: Exception) {
        throw JsonException("unable to parse date string", e)
    }
}

/**
 * Returns the date value for the specified field or `null` if not such field
 * exists.
 *
 * The date may either be encoded in JSON as a long containing the number of
 * seconds since the epoch or a formatted string.
 */
fun <F : Field> JsonObject.optDateField(field: F): ZonedDateTime? = try {
    dateField(field)
} catch (e: JsonException) {
    null
}

/**
 * Returns the object value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.objectField(field: F): JsonObject = getJSONObject(field.text)

/**
 * Returns the object value for the specified field or `null` if not such field
 * exists.
 */
fun <F : Field> JsonObject.optObjectField(field: F): JsonObject? = try {
    objectField(field)
} catch (e: JsonException) {
    null
}

/**
 * Returns the array value for the specified field.
 *
 * @throws JsonException If no such field exists.
 */
fun <F : Field> JsonObject.arrayField(field: F): JsonArray = getJSONArray(field.text)

/**
 * Returns the array value for the specified field or `null` if no such field
 * exists.
 */
fun <F : Field> JsonObject.optArrayField(field: F): JsonArray? = try {
    getJSONArray(field.text)
} catch (e: JsonException) {
    null
}

/**
 * Interprets the value for the specified field as a string then passes it to
 * [transform] which maps the string to some other type.
 *
 * @param field The field to return.
 * @param transform The transformation to apply to the string value obtained
 * from the field.
 * @return The result of [transform]
 *
 * @throws JsonException If no such field exists.
 */
fun <T, F : Field> JsonObject.mapField(field: F, transform: (String) -> T): T = transform(stringField(field))

/**
 * Interprets the value for the specified field as a string then passes it to
 * [transform] which maps the string to some other type. If the field does not
 * exist, then `null` is returned.
 *
 * @param field The field to return.
 * @param transform The transformation to apply to the string value obtained
 * from the field.
 * @return The result of [transform] or `null` if the field doesn't exist.
 */
fun <T, F : Field> JsonObject.mapOptField(field: F, transform: (String) -> T): T? {
    val str = optStringField(field) ?: return null
    return transform(str)
}

/**
 * True if [field] exists and is not the string `"null"`.
 */
fun <F : Field> JsonObject.hasNonNullField(field: F) = optStringField(field) != "null"

/**
 * Performs a set union on the mappings in `this` and [other] storing the
 * result in `this`.
 *
 * In the event of conflicting mappings, the value in [other] is preferred
 * over the value in `this`.
 *
 * If [other] is `null`, then this method does nothing.
 *
 * @param other A [JsonObject] to union with `this`.
 * @return This object.
 */
fun JsonObject.union(other: JsonObject?): JsonObject {
    if (other == null) {
        return this
    }

    val names = other.names()
    if (names != null) {
        for (i in 0 until names.length()) {
            val name = names[i] as String
            put(name, other.get(name))
        }
    }
    return this
}

/**
 * Unions this object with the one constructed by marshalling [other].
 *
 * If [other] is `null` then this method does nothing.
 *
 * @param other The object to convert to JSON and union.
 * @return This object.
 */
fun <M : Marshal<JsonObject>> JsonObject.union(other: M?): JsonObject = union(other?.marshal())

/**
 * Maps each element in a [JsonArray].
 *
 * Since we don't know how to interpret each element in the array a [producer]
 * function must be provided to extract each element from the array before
 * [mapper] can be called to transform the element.
 *
 * The value for [producer] will usually be a method reference to once of
 * [JsonArray]'s methods (e.g., `JsonArray::getString`).
 */
fun <T, U> JsonArray.map(producer: (JsonArray, Int) -> T, mapper: (T) -> U): List<U> {
    val list = mutableListOf<U>()
    for (i in 0 until this.length()) {
        list.add(mapper(producer(this, i)))
    }
    return list
}

/**
 * Equivalent to [map] where `mapper` is the identity function.
 *
 * Useful for converting a [JsonArray] to a list without mapping anything.
 */
fun <T> JsonArray.toList(producer: (JsonArray, Int) -> T): List<T> = map(producer) { x -> x }
