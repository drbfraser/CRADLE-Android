@file:Suppress("MatchingDeclarationName")

package com.cradleplatform.neptune.ext

import org.json.JSONArray
import org.json.JSONObject

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
