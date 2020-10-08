package com.cradle.neptune.model

import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

/**
 * Describes classes that can validate its properties according to their own criteria.
 * For example, [Patient] that has an age property (Patient::age) and a name property
 * (Patient::name), so we would want to restrict the age to be a valid integer (0 - 100?) and to
 * restrict the name to be an alphanumeric.
 */
interface Verifiable<T : Any> {
    /**
     * Determines if a value for a property is valid. What is valid is defined by the class
     * implementing this.
     *
     * To implement this, the class needs to check the validity of all of its properties on a
     * case-by-case basis, because each property has its own type, own conditions for validity, etc.
     * It's likely that this is implemented using a when statement to go through every property.
     * It's suggested that true is returned for properties that don't need to be validated.
     *
     * @return whether [value] for the [property] of a given class is valid
     */
    fun isValueValid(property: KProperty<*>, value: Any?): Boolean
}

/**
 * Determines validity of value for [property].
 * Note: See https://discuss.kotlinlang.org/t/reified-generics-in-interface/1628/2 for the idea of
 * using extension functions to incorporate reified types
 *
 * @throws UninitializedPropertyAccessException if checking a lateinit property
 * @return whether the value in the class's [property] is valid.
 */
@Suppress("ThrowsCount")
inline fun <reified T : Any> Verifiable<T>.isPropertyValid(property: KProperty<*>): Boolean {
    val memberProperty = T::class.memberProperties.find {
        property.name == it.name
    } ?: throw IllegalArgumentException(
        "property ${property.name} doesn't exist for ${T::class.java.simpleName}"
    )

    try {
        return isValueValid(memberProperty, memberProperty.get(this as T))
    } catch (e: InvocationTargetException) {
        if (e.cause is UninitializedPropertyAccessException) {
            // Propagate any attempts to access uninitialized lateinit vars as this exception type.
            throw UninitializedPropertyAccessException(
                (e.cause as UninitializedPropertyAccessException).message
            )
        }
        // Otherwise, something else went wrong here and we should propagate that too.
        throw e
    }
}

/**
 * @throws UninitializedPropertyAccessException if there are uninitialized lateinit properties
 * @return whether all the member values are valid
 */
inline fun <reified T : Any> Verifiable<T>.areAllMemberValuesValid(): Boolean {
    // We require KProperties of type KProperty1 in order to get the property value from the `this`
    // object. To do this, we use reified type parameters which let us treat the type of the object
    // as a class. We need the class access to use `memberProperties`, which hold KProperty1s.
    // In order to use reified type parameters, we need to make this an inline function.
    // See https://kotlinlang.org/docs/reference/inline-functions.html#reified-type-parameters.
    for (property in T::class.memberProperties) {
        if (!isValueValid(property, property.get(this as T))) {
            return false
        }
    }
    return true
}
