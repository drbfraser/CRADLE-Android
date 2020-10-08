package com.cradle.neptune.model

import java.lang.IllegalArgumentException
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KProperty
import kotlin.reflect.full.IllegalCallableAccessException
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Describes classes that can validate its properties according to its criteria.
 * For example, [Patient] has an age property (Patient::age) and a name property (Patient::name), so
 * we would want to restrict the age to be a valid Int (0 - 100?) and restrict the name to be an
 * alphanumeric String.
 */
interface Verifiable<T : Any> {
    /**
     * Determines if a value for a property is valid. What is valid is defined by the class
     * implementing this.
     *
     * To implement this, the class needs to check the validity of all of its properties on a
     * case-by-case basis, because each property has its own type, own conditions for validity, etc.
     * It's likely that this is implemented using a when statement to go through every property that
     * needs to be validated.
     *
     * It's suggested that `true` is returned for properties that don't need to be validated; it's
     * easy to do this by having an else block in a when statement that returns true, and then only
     * having the members that need validation get their own when block.
     *
     * @return whether [value] for the [property] of a given class is valid
     * @sample com.cradle.neptune.model.TestClass.isValueValid
     */
    fun isValueValid(property: KProperty<*>, value: Any?): Boolean

    /**
     * Determines validity of value for [property].
     *
     * @throws UninitializedPropertyAccessException if checking a lateinit property
     * @return whether the value in the class's [property] is valid.
     */
    @Suppress("ThrowsCount")
    fun isPropertyValid(property: KProperty<*>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T
        val memberProperty = thisTypedAsT::class.memberProperties.find {
            // properties must have unique names
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
            // Otherwise, something else went wrong here, and we should propagate that too.
            throw e
        }
    }

    /**
     * @param [shouldIgnoreAccessibility] If true, checks all member properties regardless of
     * the accessibility modifiers (e.g. private variables will be checked if calling from outside
     * of the class). Otherwise, such members are ignored when checking for validity. Defaults to
     * true.
     * @return whether all the member properties have valid values, as determined by [isValueValid].
     * @throws UninitializedPropertyAccessException if there are uninitialized lateinit properties
     */
    fun areAllMemberValuesValid(shouldIgnoreAccessibility: Boolean = true): Boolean {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T

        for (property in thisTypedAsT::class.memberProperties) {
            val oldIsAccessible = property.isAccessible
            try {
                if (shouldIgnoreAccessibility) {
                    // Suppress JVM access checks
                    property.isAccessible = true
                }

                if (!isValueValid(property, property.getter.call(thisTypedAsT))) {
                    return false
                }
            } catch (ignored: IllegalCallableAccessException) {
                // We ignore any exceptions caused by non-accessibility op the member (e.g., this
                // would happen if shouldIgnoreAccessibility is false and the class has private
                // members and the caller of this function is not in the class). In this case, such
                // members are not checked for validity.
                // We just use this way of ignoring exceptions in order to not have to deal with
                // other visibilities explicitly such as internal
            } finally {
                if (shouldIgnoreAccessibility) {
                    // We need to reset the flag, or else subsequent calls can still use the true
                    // flag.
                    property.isAccessible = oldIsAccessible
                }
            }
        }
        return true
    }
}
