@file:Suppress("MatchingDeclarationName")

package com.cradle.neptune.ext

import com.cradle.neptune.model.Marshal
import com.cradle.neptune.model.marshal
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.threeten.bp.ZonedDateTime

/**
 * Describes types which may be used as fields for retrieving and storing
 * values in a [JSONObject].
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
         * [JSONObject]'s `field` methods. It is strongly encouraged to define
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
fun <F : Field> JSONObject.put(field: F, value: Int?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.put(field: F, value: Long?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.put(field: F, value: Boolean?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.put(field: F, value: String?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.put(field: F, value: Double?): JSONObject = put(field.text, value)

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
fun <F : Field> JSONObject.put(field: F, value: ZonedDateTime?): JSONObject {
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
fun <F : Field> JSONObject.put(field: F, value: JSONObject?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value].
 *
 * If [value] is `null` the the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.put(field: F, value: JSONArray?): JSONObject = put(field.text, value)

/**
 * Maps [field] to [value] by first converting [value] into a [JSONArray].
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * This method is named differently from it's counterparts due to a limitation
 * of the JVM which is unable to determine the difference between `List<String>`
 * and `List<M> where M : Marshal<JSONObject>`.
 *
 * @return This object.
 */
fun <F : Field> JSONObject.putStringArray(field: F, value: List<String>?): JSONObject =
    put(field, value?.let { JSONArray(it) })

/**
 * Marshals [value] to JSON then stores it as a sub-object under the field name.
 *
 * If [value] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field, M : Marshal<JSONObject>> JSONObject.put(field: F, value: M?): JSONObject =
    put(field.text, value?.marshal())

/**
 * Marshals a list of values into a JSON array storing it as a sub-array under
 * a given field name.
 *
 * If [values] is `null` then the mapping for [field] will be removed from this
 * object.
 *
 * @return This object.
 */
fun <F : Field, M : Marshal<JSONObject>> JSONObject.put(field: F, values: List<M>?): JSONObject =
    put(field.text, values?.map(::marshal)?.let { JSONArray(it) })

/**
 * Returns the string value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.stringField(field: F): String = getString(field.text)

/**
 * Returns the string value for the specified field or `null` if it does not
 * exist.
 *
 * @return The string value contained in the field.
 */
fun <F : Field> JSONObject.optStringField(field: F): String? = try {
    val value = getString(field.text)

    // Sometimes, we'll use the string "null" to mean a real `null` and not the
    // text "null" (yes very confusing, I know). To compensate for this, when
    // parsing an optional string, the text "null" will be treated as a literal
    // `null` and not the string "null". Note that this is not the case for the
    // non-optional variant of this method.
    if (value == "null") {
        null
    } else {
        value
    }
} catch (e: JSONException) {
    null
}

/**
 * Returns the integer value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.intField(field: F): Int = getInt(field.text)

/**
 * Returns the integer value for the specified field or `null` if it does not
 * exist.
 *
 * @return The boolean value contained in the field.
 */
fun <F : Field> JSONObject.optIntField(field: F): Int? = try {
    getInt(field.text)
} catch (e: JSONException) {
    null
}

/**
 * Returns the long value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.longField(field: F): Long = getLong(field.text)

/**
 * Returns the long value for the specified field or `null` if it does not
 * exist.
 *
 * @return The long value contained in the field.
 */
fun <F : Field> JSONObject.optLongField(field: F): Long? = try {
    getLong(field.text)
} catch (e: JSONException) {
    null
}

/**
 * Returns the double value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.doubleField(field: F): Double = getDouble(field.text)

/**
 * Returns the double value for the specified field or `null` if it does not
 * exist.
 *
 * @return The double value contained in the field.
 */
fun <F : Field> JSONObject.optDoubleField(field: F): Double? = try {
    getDouble(field.text)
} catch (e: JSONException) {
    null
}

/**
 * Returns the boolean value for the specified field.
 * Also accepts 0 for false and 1 for true.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.booleanField(field: F): Boolean {
    return try {
        getBoolean(field.text)
    } catch (exception: JSONException) {
        // Might be represented as a 1 or 0
        getInt(field.text).let {
            when (it) {
                0 -> false
                1 -> true
                else -> throw exception
            }
        }
    }
}

/**
 * Returns the boolean value for the specified field or `null` if it does not
 * exist.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.optBooleanField(field: F): Boolean = optBoolean(field.text, false)

/**
 * Returns the object value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.objectField(field: F): JSONObject = getJSONObject(field.text)

/**
 * Returns the object value for the specified field or `null` if not such field
 * exists.
 */
fun <F : Field> JSONObject.optObjectField(field: F): JSONObject? = try {
    objectField(field)
} catch (e: JSONException) {
    null
}

/**
 * Returns the array value for the specified field.
 *
 * @throws JSONException If no such field exists.
 */
fun <F : Field> JSONObject.arrayField(field: F): JSONArray = getJSONArray(field.text)

/**
 * Returns the array value for the specified field or `null` if no such field
 * exists.
 */
fun <F : Field> JSONObject.optArrayField(field: F): JSONArray? = try {
    getJSONArray(field.text)
} catch (e: JSONException) {
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
 * @throws JSONException If no such field exists.
 */
inline fun <T, F : Field> JSONObject.mapField(field: F, transform: (String) -> T): T =
    transform(stringField(field))

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
inline fun <T, F : Field> JSONObject.mapOptField(field: F, transform: (String) -> T): T? {
    val str = optStringField(field) ?: return null
    return transform(str)
}

/**
 * True if [field] exists and is not the string `"null"`.
 */
fun <F : Field> JSONObject.hasNonNullField(field: F) = optStringField(field) != "null"

/**
 * Performs a set union on the mappings in `this` and [other] storing the
 * result in `this`.
 *
 * In the event of conflicting mappings, the value in [other] is preferred
 * over the value in `this`.
 *
 * If [other] is `null`, then this method does nothing.
 *
 * @param other A [JSONObject] to union with `this`.
 * @return This object.
 */
fun JSONObject.union(other: JSONObject?): JSONObject {
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
fun <M : Marshal<JSONObject>> JSONObject.union(other: M?): JSONObject = union(other?.marshal())

/**
 * Converts this JSON array into a list.
 *
 * Since the element type of [JSONArray] is unknown at compile time, a
 * producer is required to extract elements from the array. Usually this
 * takes the form of a method reference.
 *
 * ## Example
 *
 * To convert a [JSONArray] into a list of [Long]s, one can simply do:
 *
 * ```
 * val l = array.toList(JSONArray::getLong)
 * ```
 *
 * @param producer takes a reference to the wrapped [JSONArray] and an index
 *  and produces a value which will be appended to the list
 * @return the JSON array as a list of elements of type [T]
 */
inline fun <T> JSONArray.toList(producer: (JSONArray, Int) -> T): List<T> =
    (0 until length()).map { producer(this, it) }

/**
 * Maps each element of this JSON array into a new value and returns the result
 * as a list.
 *
 * Equivalent to calling `List::map` on the result of `JSONArray::toList`.
 *
 * ## Example
 *
 * ```
 * val lengths = array.map(JSONArray::getString) { it.length() }
 * ```
 *
 * @param producer takes a reference to the wrapped [JSONArray] and an index
 *  and produces a value which will be appended to the list
 * @param mapper takes a result of [producer] and converts into into a new
 *  value of type [U]
 * @return the result of mapping [mapper] over each element in this JSON array
 */
inline fun <T, U> JSONArray.map(producer: (JSONArray, Int) -> T, mapper: (T) -> U): List<U> =
    toList(producer).map(mapper)
