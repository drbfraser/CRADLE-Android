package com.cradle.neptune.model

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.reflect.KProperty

class VerifiableTests {
    private val validNames = listOf(
        "abc", "a", "null", "1234567890", "123456789012345", "123456789012346"
    )
    private val validAges = 0..120
    private val invalidNames = listOf(
        "", " ", "1234567890123467", "aaaaa aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    )
    private val invalidAges = -1000..-1 union 121..1000

    @Test
    fun isValid_usingValidValues() {
        val testClass = TestClass("my name", 50, "lateinit")
        assert(testClass.areAllMemberValuesValid())

        validNames.forEach {
            assert(testClass.isValueValid(TestClass::nameMax15Chars, it)) {
                "expected $it to be a valid name"
            }
        }
        validAges.forEach {
            assert(testClass.isValueValid(TestClass::age, it)) {
                "expected $it to be a valid age"
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingValidValues() {
        for (name in validNames) {
            for (age in validAges) {
                val validClass = TestClass(name, age, "some lateinit thing")
                assert(validClass.areAllMemberValuesValid()) {
                    "expected TestClass with name $name and age $age to be valid"
                }
            }
        }
    }

    @Test
    fun isValid_usingInvalidValues() {
        val testClass = TestClass(
            "my name is going to be over 15 chars",
            50,
            "lateinit"
        )
        assert(!testClass.areAllMemberValuesValid())

        val testClassWithoutLateinit = TestClass(
            "name and age are valid",
            50
        )
        assertThrows(UninitializedPropertyAccessException::class.java) {
            testClassWithoutLateinit.isPropertyValid(TestClass::someLateinitThing)
        }

        invalidNames.forEach {
            assert(!testClass.isValueValid(TestClass::nameMax15Chars, it)) {
                "expected $it to be an invalid name"
            }
        }

        invalidAges.forEach {
            assert(!testClass.isValueValid(TestClass::age, it)) {
                "expected $it to be an invalid age"
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingInvalidValues() {
        for (name in invalidNames) {
            for (age in invalidAges) {
                val invalidClass = TestClass(name, age)
                assert(!invalidClass.areAllMemberValuesValid()) {
                    "expected TestClass with name $name and age $age to be invalid"
                }
            }
        }
    }

    @Test
    fun areAllMemberValuesValid_usingMixOfValidAndInvalidValues() {
        for (name in invalidNames union validNames) {
            for (age in invalidAges union invalidAges) {
                val testClass = TestClass(name, age)
                val expectedToBeValid = name in validNames && age in validAges

                assert(testClass.areAllMemberValuesValid() == expectedToBeValid) {
                    "expected TestClass with name $name and age $age to be " +
                        if (expectedToBeValid) "valid" else "invalid"
                }
            }
        }
    }
}

internal class TestClass(
    val nameMax15Chars: String,
    val age: Int
): Verifiable<TestClass> {

    constructor(name: String, age: Int, lateinitString: String) : this(name, age) {
        someLateinitThing = lateinitString
    }

    lateinit var someLateinitThing: String
    /**
     * To implement this, you need to check the validity on a case-by-case basis, because each
     * property has its own type, own conditions for validity, etc.
     */
    override fun isValueValid(property: KProperty<*>, value: Any?): Boolean {
        return when (property) {
            TestClass::nameMax15Chars -> {
                val typed = value as String
                typed.isNotBlank() && typed.length <= 15
            }
            TestClass::age -> {
                val typed = value as Int
                typed in 0..120
            }
            TestClass::someLateinitThing -> {
                val typed = value as String
                typed.isNotEmpty()
            }
            else -> true
        }
    }
}