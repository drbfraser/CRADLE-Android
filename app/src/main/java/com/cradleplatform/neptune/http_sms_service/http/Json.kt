package com.cradleplatform.neptune.http_sms_service.http

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sum type which represents a blob of JSON data.
 */
sealed class Json {

    /**
     * Unwraps this JSON data as an object.
     *
     * Returns `null` if this is a [JsonArray] and not a [JsonObject].
     */
    val obj: JSONObject?
        get() = when (this) {
            is JsonObject -> value
            is JsonArray -> null
        }

    /**
     * Unwraps this JSON data as an array.
     *
     * Returns `null` if this is a [JsonObject] and not a [JsonArray]
     */
    val arr: JSONArray?
        get() = when (this) {
            is JsonObject -> null
            is JsonArray -> value
        }

    /**
     * Converts this JSON data into a string without any line breaks or
     * indentations.
     *
     * @return a string representation of the JSON data
     */
    abstract override fun toString(): String

    /**
     * Converts this JSON data into a string.
     *
     * @param indentFactor the number of spaces to used when indenting nested
     *  structures
     * @return a string representation of the JSON data
     */
    abstract fun toString(indentFactor: Int): String

    /**
     * Converts this JSON data into a byte array.
     *
     * @return the JSON data as a byte array
     */
    fun marshal() = toString().toByteArray()

    companion object {
        /**
         * Converts a byte array into a [JsonObject] or [JsonArray].
         *
         * @param data the byte array to parse
         * @return a [Json] variant
         * @throws org.json.JSONException if unable to parse [data]
         */
        fun unmarshal(data: ByteArray): Json {
            val str = String(data).trimStart()
            return if (str.firstOrNull() == '{') {
                JsonObject(str)
            } else {
                JsonArray(str)
            }
        }
    }
}

/**
 * Wraps a [JSONObject] in a [Json] variant allowing polymorphism between the
 * two JSON types.
 *
 * This class is only meant as a wrapper. To build or manipulate a JSON object
 * please use [JSONObject] instead.
 *
 * @property value Underlying [JSONObject] value
 */
class JsonObject(val value: JSONObject) : Json() {

    constructor() : this(JSONObject())

    constructor(map: Map<String, Any>) : this(JSONObject(map))

    constructor(string: String) : this(JSONObject(string))

    override fun toString() = value.toString()

    override fun toString(indentFactor: Int): String = value.toString(indentFactor)
}

/**
 * Wraps a [JSONArray] in a [Json] variant allowing polymorphism between the
 * two JSON types.
 *
 * This class is only meant as a wrapper. To build or manipulate a JSON array
 * please use [JSONArray] instead.
 *
 * @property value Underlying [JSONArray] value
 */
class JsonArray(val value: JSONArray) : Json() {

    constructor() : this(JSONArray())

    constructor(string: String) : this(JSONArray(string))

    override fun toString() = value.toString()

    override fun toString(indentFactor: Int): String = value.toString(indentFactor)
}
