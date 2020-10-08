package com.cradle.neptune.model

import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KProperty
import kotlin.reflect.full.IllegalCallableAccessException
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

    /**
     * Determines validity of value for [property].
     *
     * @throws UninitializedPropertyAccessException if checking a lateinit property
     * @return whether the value in the class's [property] is valid.
     */
    fun isPropertyValid(property: KProperty<*>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T
        val memberProperty = thisTypedAsT::class.memberProperties.find {
            property.name == it.name
        } ?: throw IllegalArgumentException(
            "property ${property.name} doesn't exist for ${thisTypedAsT::class.java.simpleName}"
        )

        try {
            return isValueValid(memberProperty, memberProperty.getter.call(this))
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
     * @return whether all the member values are valid
     * @throws UninitializedPropertyAccessException if there are uninitialized lateinit properties
     */
    fun areAllMemberValuesValid(): Boolean {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T

        for (property in thisTypedAsT::class.memberProperties) {
            try {
                if (!isValueValid(property, property.getter.call(thisTypedAsT))) {
                    return false
                }
            } catch (ignored: IllegalCallableAccessException) {
                // Ignore any exceptions caused by non-accessibility from the caller (e.g., trying to
                // access a private variable). Ideally, it should also check private members; however,
                // since this is an inline function, this would be going through
            }
        }
        return true
    }
}
