package com.cradle.neptune.model

import android.content.Context
import androidx.lifecycle.LiveData
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
     * It's recommended that the actual validator function for a class be put in a Companion object
     * for that class (have the Companion implement the [Verifier] interface) so that other classes
     * can access it without needing to create an instance first. Then, the member function can just
     * invoke the function inside of the Companion object. See the sample (CTRL + Q in Android
     * Studio) for an example.
     *
     * @sample com.cradle.neptune.model.TestClass.isValueForPropertyValid
     * @sample com.cradle.neptune.model.TestClass.Companion.isValueValid
     *
     * @param property The property to check [value] for. You **must** use `ClassName::PropertyName`,
     * not `::PropertyName`, not `this::PropertyName`, etc.
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
     * For the [property], you **must** use `ClassName::PropertyName`, not `::PropertyName`, not
     * `this::PropertyName`, etc.
     *
     * @throws UninitializedPropertyAccessException if checking a lateinit property
     * @return whether the value in the class's [property] is valid.
     */
    @Suppress("ThrowsCount")
    fun isPropertyValid(property: KProperty<*>, context: Context): Pair<Boolean, String> {
        try {
            return isValueForPropertyValid(property, property.getter.call(this), context)
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
    @Suppress("NestedBlockDepth")
    fun getAllMembersWithInvalidValues(
        context: Context,
        shouldIgnoreAccessibility: Boolean = true,
        shouldStopAtFirstError: Boolean = false
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
                    if (shouldStopAtFirstError) {
                        return listOfInvalidProperties
                    }
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

    /**
     * Determine if this is a valid instance as determined by [getAllMembersWithInvalidValues]
     * (which is dependent on [isValueForPropertyValid]
     */
    fun isValidInstance(context: Context) =
        getAllMembersWithInvalidValues(context, shouldStopAtFirstError = true).isEmpty()
}

/**
 * A verification function, meant to be used as an object inside of the classes that implement
 * [Verifiable].
 */
interface Verifier<in T> {
    /**
     * Determines if the [value] for property [property] is valid. If valid, the [Pair] returned
     * will have a Boolean value of true in the first component. Otherwise, false will be in the
     * first component and an error message will be present ([context] is required to get a
     * localized error message).
     *
     * When implementing, use [setupDependentPropertiesMap] if checking a certain property requires
     * the values of other properties.
     *
     * Example call:
     *
     * ```
     * val readingBuilder: LiveDataDynamicModelBuilder()
     * ...
     * Patient.isValueValid(Patient::id, "32252", context, null, readingBuilder.publicMap)
     * ```
     *
     * For [property], you **must** use `ClassName::PropertyName`, not `::PropertyName`, not
     * `this::PropertyName`, etc.
     *
     * @sample com.cradle.neptune.model.PregnancyRecord.Companion.isValueValid
     *
     * @param instance An instance of the object to take current values from for properties that
     * check other properties for validity. Optional, but don't specify both a non-null [instance]
     * and a [currentValues] map.
     * @param currentValues A Map of KProperty.name to their values to describe current values for
     * properties that check other properties for validity. Optional only if not passing in an
     * instance. (The values in here take precedence if you do.)
     */
    fun isValueValid(
        property: KProperty<*>,
        value: Any?,
        context: Context,
        instance: T? = null,
        currentValues: Map<String, Any?>? = null
    ): Pair<Boolean, String>
}

/**
 * Sets up a dependent properties map when an instance is given. This map is used when implementing
 * [Verifiable.isValueForPropertyValid] to access dependent properties.
 *
 * If we need to access to the values of other properties, we use [setupDependentPropertiesMap] to
 * get them. If we don't then don't call it. See the sample by CTRL+Q in Android Studio.
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
 * inefficient, and might not work properly because the user might not have
 * given values for mandatory properties for Patient yet.
 *
 * The solution is to have the caller implement some map that contains all
 * the properties so far, and then use that to get the dependent
 * properties.
 *
 * @sample com.cradle.neptune.model.PregnancyRecord.Companion.isValueValid
 *
 * @param instance An instance to check against. The current values will be taken from this instance
 * if a current values map is not given, but the current values map is given priority.
 * @param currentValuesMap The current values to get dependent properties from
 * @param dependentProperties A declaration of all the properties that are needed.
 * @return A mapping from the names of the properties to their current values. The names are
 * obtained by KProperty.name.
 *
 * @throws IllegalArgumentException if [instance] is null and no [currentValuesMap] were given. We
 * make [instance] nullable to make sure that this function can also serve as a check.
 */
fun setupDependentPropertiesMap(
    instance: Any?,
    currentValuesMap: Map<String, Any?>?,
    vararg dependentProperties: KProperty<*>
): Map<String, Any?> =
    currentValuesMap?.run {
        if (this.values.find { it is LiveData<*>? } != null) {
            // If this was given by a LiveDataDynamicModelBuilder, extract the values.
            this.mapValues { (it.value as LiveData<*>?)?.value }.toMap()
        } else {
            this
        }
    } ?: if (instance == null) {
            throw IllegalArgumentException("null instance requires non-null dependentPropertiesMap")
        } else {
            // Since there is an instance, create a new map just containing the values taken from
            // that instance. This is so that we don't have to repeat code in the Verifier.
            // Verifier just uses mapName[ClassName::PropertyName.name] syntax to obtain a
            // dependency
            dependentProperties.map { it.name to it.getter.call(instance) }.toMap()
        }
