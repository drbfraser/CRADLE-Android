package com.cradle.neptune.model

import java.lang.Exception

/**
 * Describes types which can be converted into a data representation for
 * storage or transmission.
 *
 * Provides a single method, [marshal], for converting this object into
 * a [Data] object.
 *
 * For implementors which only marshal to a single type, the class itself
 * should implement this interface:
 *
 * ```
 * class Person(val name: String, age: Int) : Marshal<JSONObject> {
 *     override fun marshal() = with(JSONObject()) {
 *         put("name", name)
 *         put("age", age)
 *     }
 * }
 *
 * val person = Person("a", 20)
 * val a = person.marshal()
 * val b = marshal(person)
 * ```
 *
 * If a class wishes to marshal to many different types, they can define an
 * inner class for each type:
 *
 * ```
 * class Person(val name: String, val age: Int) {
 *     inner class ToJson : Marshal<JSONObject> {
 *         override fun marshal(): JSONObject = with(JSONObject()) {
 *             put("name", name)
 *             put("age", age)
 *         }
 *     }
 *
 *     inner class ToString : Marshal<String> {
 *         override fun marshal(): String = "$name:$age"
 *     }
 * }
 *
 * val person = Person("a", 20)
 * val a = person.ToJson().marshal()
 * val b = person.ToString().marshal()
 *
 * val c = marshal(person.ToJson())
 * val d = marshal(person.ToString())
 * ```
 *
 * @param Data The data type to marshal into.
 */
interface Marshal<Data> {
    /**
     * Marshals this object into a [Data] object.
     */
    fun marshal(): Data
}

/**
 * Describes types which can be constructed from a data representation.
 *
 * Provides a single method, [unmarshal], for converting a [Data] object into
 * some [Result] type. Commonly, [Result] will be the implementing type itself
 * but this is not required.
 *
 * For implementors which only unmarshal from a single data type, this
 * interface should be implemented by the implementor's `companion object`:
 *
 * ```
 * class Person(val name: String = "", val age: Int = 0) {
 *     companion object : Unmarshal<Person, JSONObject> {
 *         override fun unmarshal(data: JSONObject) = Person().apply {
 *             name = data.getString("name")
 *             age = data.getInt("age")
 *         }
 *     }
 * }
 *
 * val json = ...
 * // A person can be constructed like so:
 * val a = Person.unmarshal(json)
 * // or, using the global function:
 * val b = unmarshal(Person, json)
 * ```
 *
 * If a type needs to be unmarshalled from many different data types, then it
 * can define an anonymous inner class for each data type like so:
 *
 * ```
 * class Person(val name: String = "", val age: Int = 0) {
 *     object FromJson : Unmarshal<Person, JSONObject> {
 *         override fun unmarshal(data: JSONObject) = Person().apply {
 *             // Parse JSON an construct...
 *         }
 *     }
 *
 *     object FromString : Unmarshal<Person, String> {
 *         override fun unmarshal(data: String) = Person().apply {
 *             // Parse string and construct...
 *         }
 *     }
 * }
 *
 * val json = ...
 * val str = ...
 *
 * // Construct using inner objects:
 * val a = Person.FromJson.unmarshal(json)
 * val b = Person.FromString.unmarshal(str)
 *
 * // Construct using global function:
 * val c = unmarshal(Person.FromJson, json)
 * val d = unmarshal(Person.FromString, str)
 * ```
 */
interface Unmarshal<Result, Data> {
    /**
     * Constructs a [Result] object from a [Data] object.
     */
    fun unmarshal(data: Data): Result

    /**
     * Attempts to construct a [Result] object from a [Data] object. Returns
     * `null` if an exception was thrown during the construction process.
     */
    fun maybeUnmarshal(data: Data): Result? = try {
        unmarshal(data)
    } catch (e: Exception) {
        null
    }
}

/**
 * A function variant of [Marshal.marshal] which converts an object of type [T]
 * into a [Data] object.
 *
 * @see Marshal
 * @param T The type to convert from.
 * @param Data The type to convert to.
 * @param obj The object to marshal.
 * @return A [Data] object constructed from [obj].
 */
fun <Data, T : Marshal<Data>> marshal(obj: T): Data = obj.marshal()

/**
 * A function variant of [Unmarshal.unmarshal] which constructs an object of
 * type [T] from a [Data] object.
 *
 * @see Unmarshal
 * @param T The type to convert to.
 * @param Data The type to convert from.
 * @param self The value to unmarshal. Usually this will be the companion
 * object of the type being constructed.
 * @param data The data to convert.
 * @return A [T] object constructed from [data].
 */
fun <T, Data, U : Unmarshal<T, Data>> unmarshal(self: U, data: Data): T = self.unmarshal(data)

/**
 * A function variant of [Unmarshal.maybeUnmarshal] which constructs an object
 * of type [T] from a [Data] object returning `null` if an exception was thrown
 * instead of propagating the exception.
 *
 * @see Unmarshal
 * @param T The type to convert to.
 * @param Data The type to convert from.
 * @param self The value to unmarshal. Usually this will be the companion
 * object of the type being constructed.
 * @param data The data to convert.
 * @return A [T] object constructed from [data].
 */
fun <T, Data, U : Unmarshal<T, Data>> maybeUnmarshal(self: U, data: Data): T? =
    self.maybeUnmarshal(data)
