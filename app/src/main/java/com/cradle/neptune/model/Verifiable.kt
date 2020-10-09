package com.cradle.neptune.model

import android.content.Context
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
interface Verifiable<in T : Any> {
    /**
     * Determines if a [value] for a [property] is valid. What is valid is defined by the class
     * implementing this. [context] is needed to get a localized error message.
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
     * It's recommended that the validator function for an class be put in a Companion object for
     * that class so that other classes can access it without needing to create an instance first.
     * Then, the member function can just invoke the function inside of the Companion object.
     * See the sample (CTRL + Q in Android Studio) for an example.
     *
     * @sample com.cradle.neptune.model.TestClass.isValueForPropertyValid
     * @sample com.cradle.neptune.model.TestClass.Companion.isValueValid
     *
     * @param property The property to check [value] for.
     * @param value The value to test. This should be the same type the type in [property].
     * @param context A Context required to get localized error messages.
     * @return A [Pair], where the left value is whether the value is valid for the given property,
     * and the right value is an error message. The error message should be ignored if the value is
     * valid.
     */
    fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String>

    /**
     * Determines validity of value for [property] for this object. [context] is needed to get a
     * localized error message.
     *
     * @throws UninitializedPropertyAccessException if checking a lateinit property
     * @return whether the value in the class's [property] is valid.
     */
    @Suppress("ThrowsCount")
    fun isPropertyValid(property: KProperty<*>, context: Context): Pair<Boolean, String> {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T

        // Find the property as a member property.
        val memberProperty = thisTypedAsT::class.memberProperties.find {
            // Properties must have unique names.
            property.name == it.name
        } ?: throw IllegalArgumentException(
            "property ${property.name} doesn't exist for ${thisTypedAsT::class.java.simpleName}"
        )

        try {
            return isValueForPropertyValid(memberProperty, memberProperty.getter.call(this), context)
        } catch (e: InvocationTargetException) {
            if (e.cause is UninitializedPropertyAccessException) {
                // Propagate any attempts to access uninitialized lateinit vars as an
                // UninitializedPropertyAccessException
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
     * @return A List of [Pair] where each pair is of the form:
     *     <Property name with invalid value, Error message for property name>.
     * The List is empty if all values are valid.
     * @throws UninitializedPropertyAccessException if there are uninitialized lateinit properties
     */
    fun getAllMembersWithInvalidValues(
        context: Context,
        shouldIgnoreAccessibility: Boolean = true
    ): List<Pair<String, String>> {
        @Suppress("UNCHECKED_CAST")
        val thisTypedAsT = this as T
        val listOfInvalidProperties: MutableList<Pair<String, String>> = mutableListOf()

        for (property in thisTypedAsT::class.memberProperties) {
            val oldIsAccessible = property.isAccessible
            try {
                if (shouldIgnoreAccessibility) {
                    // Suppress JVM access checks
                    property.isAccessible = true
                }

                // First value in the pair is false; second value is the error message.
                val result = isValueForPropertyValid(property, property.getter.call(thisTypedAsT), context)
                if (!result.first) {
                    listOfInvalidProperties.add(Pair(property.name, result.second))
                }
            } catch (ignored: IllegalCallableAccessException) {
                // We ignore any exceptions caused by non-accessibility of the member (e.g., this
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
        return if (listOfInvalidProperties.isEmpty()) {
            // Statically-allocated empty list as an optimization
            emptyList()
        } else {
            listOfInvalidProperties
        }
    }
}

/**
 * Sets up a dependent properties map when an instance is given. This map is used when implementing
 * [Verifiable.isValueForPropertyValid] to access properties that are
 * dependent.
 *
 * ### Background
 * The `dependentPropertiesMap` solves the dependent properties problem in
 * the original design.
 *
 * For a property P of a class T where the validity of values for P depend
 * on the values of other properties in an instance of T, we can't reliably
 * test the validity of a value for P from another different class without
 * creating an instance of T.
 *
 * For example, for a Patient, a null GestationalAge is valid iff the
 * patient is not pregnant or is male. So, if we want to determine if a
 * null GestationalAge is valid, we need an instance of a Patient to get
 * the other properties. However, if we're filling out a form and we want
 * to do real-time validation as the user enters, this would mean creating
 * new Patient instances every time the user enters something. This is
 * inefficient.
 *
 * The solution is to have the caller implement some map that contains all
 * the properties so far, and then use that to get the dependent
 * properties. It can be extended in the future to just use the map that
 * the LiveDataDynamicModelBuilder has to save the trouble of having to
 * create new maps every time an input is entered.
 *
 * @throws IllegalArgumentException if [instance] is null. We make [instance] nullable to make sure
 * that this function can also serve as a check.
 */
fun setupDependentPropertiesMapForInstance(
    instance: Any?,
    givenDependentPropertiesMap: Map<KProperty<*>, Any?>?,
    vararg existingProperties: KProperty<*>
): Map<KProperty<*>, Any?> =
    givenDependentPropertiesMap
        ?: if (instance == null) {
            throw IllegalArgumentException("null instance requires non-null dependentPropertiesMap")
        } else {
            // since there is an instance, create a new map just containing the values. This is so
            // that we don't have to repeat code.
            existingProperties.map { it to it.getter.call(instance) }.toMap()
        }
