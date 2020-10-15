package com.cradle.neptune.utilities

import com.cradle.neptune.utilitiles.DynamicModelBuilder
import com.cradle.neptune.utilitiles.dynamic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class DynamicModelBuilderTests {

    @Test
    fun dynamic_ifAllFieldsSupplied_constructObject() {
        val map = mapOf("name" to "Maya", "age" to 20, "email" to "test@example.com")
        val person by dynamic<Person>(map)
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertEquals("test@example.com", person.email)
    }

    @Test
    fun dynamic_ifNullGivenForNonNullField_throwStuff() {
        val map = mapOf("name" to null, "age" to null)
        assertThrows(IllegalArgumentException::class.java) {
            // Get value is required here as the `by` notation is lazy.
            dynamic<Person>(map).getValue()
        }
    }

    @Test
    fun dynamic_ifNullableFieldMissing_constructObjectWithoutIt() {
        val map = mapOf("name" to "Maya", "age" to 20)
        val person by dynamic<Person>(map)
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertNull(person.email)
    }

    @Test
    fun dynamic_ifNonNullableFieldMissing_throwException() {
        val map = mapOf("name" to "Maya")
        assertThrows(IllegalArgumentException::class.java) {
            // Get value is required here as the `by` notation is lazy.
            dynamic<Person>(map).getValue()
        }
    }

    @Test
    fun dynamic_ifFieldWithDefaultValueSupplied_overrideDefaultValue() {
        val map = mapOf("name" to "CMPT 415", "units" to 4)
        val course by dynamic<Course>(map)
        assertEquals("CMPT 415", course.name)
        assertEquals(4, course.units)
    }

    @Test
    fun dynamic_ifFieldWithDefaultValueMissing_useDefaultValue() {
        val map = mapOf("name" to "CMPT 415")
        val course by dynamic<Course>(map)
        assertEquals("CMPT 415", course.name)
        assertEquals(3, course.units)
    }

    @Test
    fun dynamic_ifNullableFieldWithDefaultValueMissing_useDefaultValue() {
        val map = mapOf("name" to "admin")
        val user by dynamic<User>(map)
        assertEquals("admin", user.name)
        assertEquals("password", user.password)
    }

    @Test
    fun dynamicModelBuilder_setWithStringKey_populatesInternalMap() {
        val person = with(DynamicModelBuilder()) {
            set("name", "Maya")
            set("age", 20)
            build<Person>()
        }
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertNull(person.email)
    }

    @Test
    fun dynamicModelBuilder_setWithRefectionField_populatesInternalMap() {
        val person = with(DynamicModelBuilder()) {
            set(Person::name, "Maya")
            set(Person::age, 20)
            build<Person>()
        }
        assertEquals("Maya", person.name)
        assertEquals(20, person.age)
        assertNull(person.email)
    }

    @Test
    fun dynamicModelBuilder_ifMissingField_returnNull() {
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            with(DynamicModelBuilder()) {
                set(Person::name, "Maya")
                build<Person>()
            }
        }
    }
}

/* Classes for Testing */

data class Person(val name: String, val age: Int, val email: String?)

data class Course(val name: String, val units: Int = 3)

data class User(val name: String, val password: String? = "password")
