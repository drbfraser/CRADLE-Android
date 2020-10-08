package com.cradle.neptune.model

import android.content.Context
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
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
    fun isPropertyValid() {
        // Never do this.
        val simpleNumber = SimpleNumber(4)
        assertThrows(IllegalArgumentException::class.java) {
            simpleNumber.isPropertyValid(PositiveRationalNumber::numerator, mockContext)
        }
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
        return isValueValid(this, property, value, context)
    }

    companion object {
        @JvmStatic
        fun isValueValid(
            rationalNumberToCheckAgainst: PositiveRationalNumber,
            property: KProperty<*>,
            value: Any?,
            context: Context
        ): Pair<Boolean, String> {
            // The validity depends on the members that are already set. It doesn't always have to
            // be just independent checks.
            if (property == PositiveRationalNumber::denominator) {
                val typedValue = value as Int
                if (typedValue == 0) {
                    return Pair(false, "Denominator cannot be 0")
                } else if (typedValue.sign != rationalNumberToCheckAgainst.numerator.sign) {
                    return Pair(false, "Denominator makes the rational number negative")
                }
            }
            else if (property == PositiveRationalNumber::numerator) {
                val typedValue = value as Int
                if (typedValue == 0) {
                    return Pair(false, "Numerator cannot be 0, because it's not positive")
                } else if (typedValue.sign != rationalNumberToCheckAgainst.denominator.sign) {
                    return Pair(false, "Numerator makes the rational number negative")
                }
            }
            return Pair(true, "")
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
        // Note: This is implemented in the Companion so that other objects can use it without
        // creating an instance of TestClass.
        return isValueValid(property, value, context)
    }

    companion object {
        @JvmStatic
        fun isValueValid(
            property: KProperty<*>,
            value: Any?,
            context: Context
        ): Pair<Boolean, String> {
            when (property) {
                TestClass::nameMax15Chars -> {
                    // Don't need to have a specific error message, but good to have.
                    val typed = value as String
                    if (typed.isBlank() || typed.isEmpty()) {
                        return Pair(false, "Name cannot be empty")
                    } else if (typed.length > 15) {
                        return Pair(false, "Name cannot be more than 15 characters")
                    }
                    return Pair(true, "")
                }
                TestClass::age -> {
                    val typed = value as Int
                    if (typed < 0) {
                        return Pair(false, "Age must be non-negative")
                    } else if (typed > 120) {
                        return Pair(false, "Age must be less than 120")
                    }
                    return Pair(true, "")
                }
                else -> return Pair(true, "")
            }
        }
    }
}
