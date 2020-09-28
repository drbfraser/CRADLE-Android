package com.cradle.neptune.utilitiles

import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

/**
 * Dynamically constructs an object of type [T] from a set of values.
 *
 * By reflection, the [DynamicConstructor] delegate analyzes the first
 * constructor of the requested type and extracts the required values from
 * [map] to construct and instance of the type. This process respects optional
 * and nullable parameters handling them as you would expect.
 *
 * Since the object is constructed by a delegate, whatever it is assigned to
 * will be lazily initialized. If this is not desired the
 * [DynamicConstructor.getValue] method can be called on the delegate to force
 * it to construct the object.
 *
 * ## Example
 *
 * ```
 * data class Person(val name: String, val "age": Int)
 *
 * val map = mapOf("name" to "Maya", age to 21)
 * val maya by dynamic<Person>(map)
 * println(maya)
 * ```
 *
 * @param [map] a mapping of parameter names to values.
 * @return a delegate for constructing the object
 */
inline fun <reified T : Any> dynamic(map: Map<String, Any?>) = DynamicConstructor(T::class, map)

/**
 * A delegate class used to dynamically construct objects from a map of
 * parameter names to values.
 *
 * @see dynamic
 */
@Suppress
class DynamicConstructor<T : Any>(k: KClass<T>, private val map: Map<String, Any?>) {

    private val constructor: KFunction<T> = k.constructors.firstOrNull()
        ?: throw RuntimeException("${k.simpleName} has no constructors")

    /**
     * The list of required parameters which must be supplied in [map] for
     * dynamic construction to succeed.
     */
    val requiredParameters: List<String> = constructor.parameters
        .filterNot { it.isOptional }
        .map { it.name!! }

    /**
     * A list of required parameters which are not in [map].
     */
    val missingParameters: List<String> = requiredParameters.filter { it !in map }

    /**
     * Attempts to dynamically construct an object of type [T] using it's first
     * constructor and a map containing values for its parameters.
     *
     * @return an object of type [T]
     *
     * @throws IllegalArgumentException if any of the required parameters are
     * missing or are of the wrong type
     *
     * @see dynamic
     */
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val arguments = constructor.parameters
            .filterNot { it.isOptional && it.name !in map } // Remove optional params which don't have a value
            .map { it to map[it.name] } // Map values to remaining parameters
            .toMap()

        return constructor.callBy(arguments)
    }

    /**
     * A non-delegate version of [getValue] which can be used explicitly.
     *
     * @return an object of type [T]
     *
     * @throws ReflectiveOperationException if [T] contains no constructors
     * @throws IllegalArgumentException if any of the required parameters are
     * missing or are of the wrong type
     */
    fun getValue(): T {
        val v: T by this
        return v
    }
}

class LiveDataDynamicModelBuilder : DynamicModelBuilder() {
    @Suppress("UNCHECKED_CAST")
    override val stringMap
        get() = map.mapValues {
            (it.value as MutableLiveData<Any?>?)?.value
        }.toMap()

    /**
     * Return the current [MutableLiveData] for a given parameter name. If it doesn't exist, a
     * [MutableLiveData] object with null in it is set. Prefer [getWithType] using
     * KProperty (Class::propertyName) notation to get typed [MutableLiveData] from the start.
     */
    override fun get(key: String) = map[key] as? MutableLiveData<*>
        ?: MutableLiveData(null).apply { map[key] = this }

    /**
     * Prefer using get<T> to get typed [MutableLiveData] from the start.
     */
    override fun get(key: KProperty<*>) = map[key.name] as? MutableLiveData<*>
        ?: MutableLiveData(null).apply { map[key.name] = this }

    /**
     * @return The typed [MutableLiveData] for the value of a given parameter name.
     * @param key The [KProperty] we use as a key
     */
    @JvmName("get1")
    @Suppress("UNCHECKED_CAST")
    fun <T : Any?> get(key: KProperty<T>) = map[key.name] as? MutableLiveData<T>
        ?: MutableLiveData<T>(null).apply { map[key.name] = this }

    /**
     * @return The typed [MutableLiveData] for the value of a given parameter name.
     * @param key The [KProperty] we use as a key
     * @param defaultValue The default value if the [MutableLiveData] for the given property hasn't
     * been initialized.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any?> get(key: KProperty<T>, defaultValue: T) =
        map[key.name] as? MutableLiveData<T>
            ?: MutableLiveData<T>(defaultValue).apply {
                map[key.name] = this
            }.also {
                // Guard against setting the wrong type.
                if (key.returnType != T::class.createType()) {
                    throw IllegalArgumentException("mismatched argument types")
                }
            }

    /**
     * Sets the new [value] for a given parameter name into the MutableLiveData backed by this model
     * builder using [MutableLiveData.setValue].
     */
    @UiThread
    override fun set(key: String, value: Any?): LiveDataDynamicModelBuilder {
        @Suppress("UNCHECKED_CAST")
        with(map[key] as MutableLiveData<Any?>?) {
            if (this == null) {
                map[key] = MutableLiveData(value)
            } else {
                setValue(value)
            }
        }
        return this
    }

    /**
     * Posts the new [value] for a given parameter name into the MutableLiveData backed by this
     * model builder using [MutableLiveData.postValue]. This is meant for worker threads, since
     * [MutableLiveData.setValue] is only usable on the main thread.
     */
    @WorkerThread
    fun setWorkerThread(key: String, value: Any?): LiveDataDynamicModelBuilder {
        @Suppress("UNCHECKED_CAST")
        with(map[key] as MutableLiveData<Any?>?) {
            if (this == null) {
                map[key] = MutableLiveData(value)
            } else {
                postValue(value)
            }
        }
        return this
    }

    /**
     * Posts the new [value] for a given parameter name into the MutableLiveData backed by this
     * model builder using [MutableLiveData.postValue]. This is meant for worker threads, since
     * [MutableLiveData.setValue] is only usable on the main thread.
     */
    @UiThread
    override fun set(key: KProperty<*>, value: Any?): LiveDataDynamicModelBuilder {
        @Suppress("UNCHECKED_CAST")
        with(map[key.name] as MutableLiveData<Any?>?) {
            if (this == null) {
                map[key.name] = MutableLiveData(value)
            } else {
                setValue(value)
            }
        }
        return this
    }

    /**
     * Posts the new [value] into the MutableLiveData backed by this model builder using
     * [MutableLiveData.postValue]. This is meant for worker threads, since
     * [MutableLiveData.setValue] is only usable on the main thread.
     */
    @WorkerThread
    fun setWorkerThread(key: KProperty<*>, value: Any?): LiveDataDynamicModelBuilder {
        @Suppress("UNCHECKED_CAST")
        with(map[key.name] as MutableLiveData<Any?>?) {
            if (this == null) {
                map[key.name] = MutableLiveData(value)
            } else {
                postValue(value)
            }
        }
        return this
    }

    override fun <T : Any> decompose(k: KClass<T>, obj: T) =
        super.decompose(k, obj) as LiveDataDynamicModelBuilder

    /**
     * Decomposes the member properties of [obj] adding them to this builder's internal map as
     * [MutableLiveData] entities. Note that the original [decompose] doesn't return a
     * [LiveDataDynamicModelBuilder] by default, so this is a workaround if needing to use
     * [decompose] in a builder fashion.
     *
     * @return this object, as a [LiveDataDynamicModelBuilder]
     */
    inline fun <reified T : Any> decomposeToLiveData(obj: T) = decompose(T::class, obj)
}

/**
 * An abstraction of [DynamicConstructor] which uses an internal map to keep
 * track of supplied parameters which will eventually be used to construct
 * an object.
 */
open class DynamicModelBuilder {
    protected val map: MutableMap<String, Any?> = mutableMapOf()

    protected open val stringMap get() = map.toMap()

    /**
     * Return the current value for a given parameter name.
     */
    open fun get(key: String) = map[key]

    /**
     * Return the current value for a given parameter name.
     *
     * The [KProperty.name] property is used as the map key.
     */
    open fun get(key: KProperty<*>) = map[key.name]

    /**
     * Sets the value for a given parameter name.
     *
     * @return this object
     */
    open fun set(key: String, value: Any?): DynamicModelBuilder {
        map[key] = value
        return this
    }

    /**
     * Sets the value for a given parameter name.
     *
     * The [KProperty.name] property is used as the map key.
     *
     * @return this object
     */
    open fun set(key: KProperty<*>, value: Any?): DynamicModelBuilder {
        map[key.name] = value
        return this
    }

    /**
     * Decomposes the member properties of [obj] adding them to this builder's
     * internal map.
     *
     * @return this object
     */
    open fun <T : Any> decompose(k: KClass<T>, obj: T): DynamicModelBuilder {
        for (property in k.memberProperties) {
            set(property, property.get(obj))
        }
        return this
    }

    /**
     * Decomposes the member properties of [obj] adding them to this builder's
     * internal map.
     *
     * @return this object
     */
    inline fun <reified T : Any> decompose(obj: T) = decompose(T::class, obj)

    /**
     * Returns a list of missing parameters which will need to be supplied if
     * trying to construct an instance of type [T].
     */
    fun <T : Any> missingParameters(k: KClass<T>): List<String> = constructor(k).missingParameters

    /**
     * True if an instance of type [T] is constructable by this builder.
     *
     * [T] is considered constructable if all of the required parameters for
     * its constructor are present in [map]. Note that the type of the value
     * present in the map is not checked against the required type of the
     * constructor meaning that construction may still fail if the values
     * present in [map] differ from those required by the constructor.
     */
    fun <T : Any> isConstructable(k: KClass<T>) = missingParameters(k).isEmpty()

    /**
     * True if an instance of type [T] is constructable by this builder.
     *
     * An inline, reified version of [isConstructable].
     */
    inline fun <reified T : Any> isConstructable() = isConstructable(T::class)

    /**
     * Attempts to build an instance of type [T] returning `null` if unable
     * to do so because of missing arguments.
     *
     * @throws IllegalArgumentException if any of the required parameters are
     * missing
     */
    fun <T : Any> build(k: KClass<T>): T = constructor(k).getValue()

    /**
     * Attempts to build an instance of type [T] returning `null` if unable
     * to do so because of missing arguments.
     *
     * @throws IllegalArgumentException if any of the required parameters are
     * missing
     */
    inline fun <reified T : Any> build(): T = build(T::class)

    /**
     * Returns a dynamic constructor used to construct object instances.
     */
    private fun <T : Any> constructor(k: KClass<T>) = DynamicConstructor(k, stringMap)
}
