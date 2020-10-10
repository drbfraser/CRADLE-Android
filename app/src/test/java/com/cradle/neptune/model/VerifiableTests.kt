package com.cradle.neptune.model

import android.content.Context
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.IllegalArgumentException
import kotlin.math.sign
import kotlin.reflect.KProperty

@ExtendWith(MockKExtension::class)
class VerifiableTests {
    // Names are valid if they're not empty/blank and are <= 15 chars
    private val validNames = listOf(
        "abc", "a", "null", "1234567890", "123456789012345", "123456789012346"
    )
    private val invalidNames = listOf(
        "", " ", "1234567890123467", "aaaaa aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    )

    private val validAges = 0..120
    private val invalidAges = -1000..-1 union 121..1000

    private val mockContext: Context = mockk(relaxed = true)

    @Test
    fun isValid_usingValidValues() {
        val testClass = TestClass("my name", 50, 5, "lateinit")
        assert(testClass.getAllMembersWithInvalidValues(context = mockContext).isEmpty())

        validNames.forEach {
            assert(testClass.isValueForPropertyValid(TestClass::nameMax15Chars, it, mockContext).first) {
                "expected $it to be a valid name"
            }
        }
        validAges.forEach {
            assert(testClass.isValueForPropertyValid(TestClass::age, it, mockContext).first) {
                "expected $it to be a valid age"
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingValidValues() {
        val simpleNumber = SimpleNumber(number = 10)
        assert(simpleNumber.getAllMembersWithInvalidValues(mockContext).isEmpty())

        for (name in validNames) {
            for (age in validAges) {
                val validClass = TestClass(name, age, 5, "some lateinit thing")
                assert(validClass.getAllMembersWithInvalidValues(
                    context = mockContext, shouldIgnoreAccessibility = true
                ).isEmpty()) {
                    "expected TestClass with name $name and age $age to be valid"
                }
                assert(validClass.getAllMembersWithInvalidValues(
                    context = mockContext, shouldIgnoreAccessibility = false
                ).isEmpty()) {
                    "expected TestClass with name $name and age $age to be valid"
                }
            }
        }
    }

    @Test
    fun isValid_usingInvalidValues() {
        val nameInvalid = TestClass(
            "my name is going to be over 15 chars",
            50,
            5,
            "lateinit"
        )

        val testClassWithoutLateinit = TestClass(
            "name,age,numOfPets are valid",
            50,
            5
        )
        assertThrows(UninitializedPropertyAccessException::class.java) {
            testClassWithoutLateinit.isPropertyValid(TestClass::someLateinitThing, mockContext)
        }

        invalidNames.forEach {
            assert(!nameInvalid.isValueForPropertyValid(TestClass::nameMax15Chars, it, mockContext).first) {
                "expected $it to be an invalid name"
            }
        }

        invalidAges.forEach {
            assert(!nameInvalid.isValueForPropertyValid(TestClass::age, it, mockContext).first) {
                "expected $it to be an invalid age"
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingInvalidValues() {
        for (name in invalidNames) {
            for (age in invalidAges) {
                val invalidClass = TestClass(name, age, 5, "lateinit")
                assert(invalidClass.getAllMembersWithInvalidValues(mockContext).isNotEmpty()) {
                    "expected TestClass with name {$name} and age $age to be invalid; the error " +
                        "messages are ${invalidClass.getAllMembersWithInvalidValues(mockContext)}"
                }
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingMixOfValidAndInvalidValues() {
        for (name in invalidNames union validNames) {
            for (age in invalidAges union invalidAges) {
                val testClass = TestClass(name, age, 5, "lateinit")
                val expectedToBeValid = name in validNames && age in validAges

                assert(
                    testClass.getAllMembersWithInvalidValues(mockContext).isEmpty() == expectedToBeValid
                ) {
                    if (expectedToBeValid) {
                        "expected TestClass with name \"$name\" and age $age to be valid, the error " +
                            "messages are ${testClass.getAllMembersWithInvalidValues(mockContext)}"
                    } else {
                        "expected TestClass with name \"$name\" and age $age to be invalid, the error " +
                            "messages are ${testClass.getAllMembersWithInvalidValues(mockContext)}"
                    }
                }
            }
        }
    }

    @Test
    fun isValid_PositiveRationalNumber() {
        val ratNum = PositiveRationalNumber(4, 5)
        assert(ratNum.isPropertyValid(PositiveRationalNumber::numerator, mockContext).first)
        assert(ratNum.isValueForPropertyValid(PositiveRationalNumber::numerator, 50, mockContext).first)
        assert(!ratNum.isValueForPropertyValid(PositiveRationalNumber::numerator, -50, mockContext).first)

        assert(ratNum.isPropertyValid(PositiveRationalNumber::denominator, mockContext).first)
        assert(ratNum.isValueForPropertyValid(PositiveRationalNumber::denominator, 50, mockContext).first)
        assert(!ratNum.isValueForPropertyValid(PositiveRationalNumber::denominator, -50, mockContext).first)
        assert(!ratNum.isValueForPropertyValid(PositiveRationalNumber::denominator, 0, mockContext).first)

        assert(ratNum.getAllMembersWithInvalidValues(mockContext).isEmpty())

        val divideByZero = PositiveRationalNumber(454, 0)
        assert(divideByZero.getAllMembersWithInvalidValues(mockContext).isNotEmpty())

        val alwaysNegative = PositiveRationalNumber(-5, 4)
        // Both the numerator and denominator consider themselves as invalid.
        assert(alwaysNegative.getAllMembersWithInvalidValues(mockContext).size == 2)
    }

    @Test
    fun isPropertyValid_wrongParameter() {
        // Never do this.
        val simpleNumber = SimpleNumber(4)
        assertThrows(IllegalArgumentException::class.java) {
            simpleNumber.isPropertyValid(PositiveRationalNumber::numerator, mockContext)
        }
    }

    @Test
    fun isPropertyValid_dependentProperties() {
        // it's fine to be missing gestational age when not pregnant
        val missingGestAgeNotPregnant = PregnancyRecord(
            isPregnant = false, gestationalAgeWeeks = null
        )
        assert(missingGestAgeNotPregnant.getAllMembersWithInvalidValues(mockContext).isEmpty())

        // it's not fine to be missing gestational age if pregnant
        val missingGestButPregnant = PregnancyRecord(
            isPregnant = true, gestationalAgeWeeks = null
        )
        assertEquals(1, missingGestButPregnant.getAllMembersWithInvalidValues(mockContext).size)
        // check the the only invalid property is the gestational age
        val (isValid, _) = missingGestButPregnant.isPropertyValid(
            PregnancyRecord::gestationalAgeWeeks, mockContext
        )
        assert(!isValid)

        val pregnant = PregnancyRecord(
            isPregnant = true, gestationalAgeWeeks = 5
        )
        assert(pregnant.getAllMembersWithInvalidValues(mockContext).isEmpty())
    }
}

internal data class SimpleNumber(
    val number: Int
): Verifiable<SimpleNumber> {
    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> {
        return Pair(true, "")
    }
}

internal data class PositiveRationalNumber(
    val numerator: Int,
    val denominator: Int
): Verifiable<SimpleNumber> {
    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> {
        return isValueValid(property, value, context, instance = this)
    }

    companion object : Verifier<PositiveRationalNumber> {
        @JvmStatic
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context,
            instance: PositiveRationalNumber?,
            currentValues: Map<String, Any?>?
        ): Pair<Boolean, String> {
            // The validity depends on the members that are already set. It doesn't always have to
            // be just independent checks.
            if (property == PositiveRationalNumber::denominator) {
                val typedValue = value as Int
                if (typedValue == 0) {
                    return Pair(false, "Denominator cannot be 0")
                } else if (typedValue.sign != instance?.numerator?.sign) {
                    return Pair(false, "Denominator makes the rational number negative")
                }
            }
            else if (property == PositiveRationalNumber::numerator) {
                val typedValue = value as Int
                if (typedValue == 0) {
                    return Pair(false, "Numerator cannot be 0, because it's not positive")
                } else if (typedValue.sign != instance?.denominator?.sign) {
                    return Pair(false, "Numerator makes the rational number negative")
                }
            }
            return Pair(true, "")
        }
    }
}

internal data class PregnancyRecord(
    val isPregnant: Boolean,
    val gestationalAgeWeeks: Int? = null
): Verifiable<PregnancyRecord> {

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> = isValueValid(property, value, context, this)

    companion object : Verifier<PregnancyRecord> {
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context,
            instance: PregnancyRecord?,
            currentValues: Map<String, Any?>?
        ): Pair<Boolean, String> {
            return when (property) {
                PregnancyRecord::isPregnant -> {
                    true to ""
                }
                PregnancyRecord::gestationalAgeWeeks -> with(value as Int?) {
                    val currentProperties = setupDependentPropertiesMap(
                        instance, currentValues, PregnancyRecord::isPregnant
                    )
                    if (this == null) {
                        return if (
                            currentProperties[PregnancyRecord::isPregnant.name] == false
                        ) {
                            true to ""
                        } else {
                            false to "Missing gestational age despite being pregnant"
                        }
                    }
                    true to ""
                }
                else -> {
                    true to ""
                }
            }
        }
    }
}

internal data class TestClass(
    val nameMax15Chars: String,
    val age: Int,
    val privateNumberOfPets: Int
): Verifiable<TestClass> {
    private val privateValue: Int = 50

    constructor(
        name: String,
        age: Int,
        numberOfPets: Int,
        lateinitString: String
    ) : this(name, age, numberOfPets) {
        someLateinitThing = lateinitString
    }

    lateinit var someLateinitThing: String

    override fun isValueForPropertyValid(
        property: KProperty<*>,
        value: Any?,
        context: Context
    ): Pair<Boolean, String> {
        // Note: This is implemented in the Companion so that other objects
        // can use it without creating an instance of TestClass.
        return isValueValid(property, value, context)
    }

    companion object : Verifier<TestClass> {
        @JvmStatic
        override fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context,
            instance: TestClass?,
            currentValues: Map<String, Any?>?
        ): Pair<Boolean, String> = when (property) {
            TestClass::nameMax15Chars -> with(value as String) {
                // Don't need to have a specific error message, but good to have.
                if (isBlank() || isEmpty()) {
                    return Pair(false, "Name cannot be empty")
                } else if (length > 15) {
                    return Pair(false, "Name cannot be more than 15 characters")
                }
                return Pair(true, "")
            }
            TestClass::age -> with(value as Int) {
                if (this < 0) {
                    return Pair(false, "Age must be non-negative")
                } else if (this > 120) {
                    return Pair(false, "Age must be less than 120")
                }
                return Pair(true, "")
            }
            else -> Pair(true, "")
        }
    }
}
