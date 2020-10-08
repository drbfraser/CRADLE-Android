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
     * Determines if a value for a property is valid. What is valid is as defined by the class
     * implementing this.
     *
     * To implement this, you need to check the validity on a case-by-case basis, because each
     * property has its own type, own conditions for validity, etc. It's likely that is implemented
     * using a when statement to go through every property. It's suggested that true is returned for
     * properties that don't need to be validated.
     *
     * @return whether [value] for the [property] of a given class is valid
     */
    fun isValueValid(property: KProperty<*>, value: Any?): Boolean
}

// Note: See https://discuss.kotlinlang.org/t/reified-generics-in-interface/1628/2 for idea of
// using extension functions to incorporate reified types
/**
 * Determines validity of value for [property]. Classes should override this and handle the case
 * if they have any public lateinit properties.
 *
 * @throws UninitializedPropertyAccessException if checking a lateinit property
 * @return whether the value in the class's [property] is valid.
 */
@Suppress("ThrowsCount")
inline fun <reified T : Any> Verifiable<T>.isPropertyValid(property: KProperty<*>): Boolean {
    val memberProperty = T::class.memberProperties.find {
        property.name == it.name
    } ?: throw IllegalArgumentException("property doesn't exist")

    try {
        return isValueValid(memberProperty, memberProperty.get(this as T))
    } catch (e: InvocationTargetException) {
        if (e.cause is UninitializedPropertyAccessException) {
            throw UninitializedPropertyAccessException()
        }
        throw e
    }
}

/**
 * @throws UninitializedPropertyAccessException if there are uninitialized lateinit properties
 * @return whether all the member values are valid
 */
inline fun <reified T : Any> Verifiable<T>.areAllMemberValuesValid(): Boolean {
    for (property in T::class.memberProperties) {
        if (!isValueValid(property, property.get(this as T))) {
            return false
        }
    }
    return true
}
