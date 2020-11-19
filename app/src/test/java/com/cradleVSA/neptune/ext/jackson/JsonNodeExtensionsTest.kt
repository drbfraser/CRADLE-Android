package com.cradleVSA.neptune.ext.jackson

import com.cradleVSA.neptune.ext.Field
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class JsonNodeExtensionsTest {

    private data class TestData(
        val value0: Long,
        val value1: String,
        val array: List<TestNestedData>,
        val nested: TestNestedData,
        val optionalArray: List<Int>? = emptyList()
    )

    private data class TestNestedData(val value0: Long, val value1: String)

    private enum class TestDataField(override val text: String) : Field {
        VALUE0("value0"),
        VALUE1("value1"),
        ARRAY("array"),
        NESTED("nested"),
        OPTIONAL_ARRAY(TestData::optionalArray.name)
    }

    private val mapper = jacksonObjectMapper()

    private val reader = mapper.readerFor(TestData::class.java)

    private val writer = mapper.writerFor(TestData::class.java)

    private val expected = TestData(
        value0 = 50,
        value1 = "This is a string",
        array = listOf(
            TestNestedData(value0 = 453435, value1 = "I am nested."),
            TestNestedData(value0 = 43534543345, value1 = "I am nested again")
        ),
        nested = TestNestedData(-43534, "Hello")
    )
    private val jsonString = """
{
  "value0": 50,
  "value1": "This is a string",
  "array": [ {
    "value0": 453435,
    "value1": "I am nested."
  }, {
    "value0": 43534543345,
    "value1": "I am nested again"
  } ],
  "nested": {
    "value0": -43534,
    "value1": "Hello"
  }
}
"""

    private val jsonStringWithEmptyArray = """
{
  "value0": 50,
  "value1": "This is a string",
  "array": [],
  "nested": {
    "value0": -43534,
    "value1": "Hello"
  }
}
"""

    private val jsonStringWithSingleElementArray = """
{
  "value0": 50,
  "value1": "This is a string",
  "array": [ {
    "value0": -34534534543,
    "value1": "Single element."
  } ],
  "nested": {
    "value0": -43534,
    "value1": "aabc"
  }
}
"""

    private val jsonStringWithEmptyStringForArray = """
{
  "value0": 50,
  "value1": "This is a string",
  "array": "",
  "nested": {
    "value0": -43534,
    "value1": "Hello"
  }
}
"""

    @Test
    fun `test getObject`() {
        val jsonNode = reader.readTree(jsonString)
        val nested = jsonNode.getObject<TestNestedData>(TestDataField.NESTED, codec = reader)
        assertEquals(expected.nested, nested)
    }

    @Test
    fun `test getObjectArray`() {
        // both ways will work
        val parser = reader.createParser(jsonString)
        val jsonNode = parser.codec.readTree<JsonNode>(parser)
        val nested = jsonNode.getObjectArray<TestNestedData>(TestDataField.ARRAY, parser.codec)
        assertEquals(expected.array, nested)

        val jsonNodeOfEmpty = reader.readTree(jsonStringWithEmptyArray)
        val empty = jsonNodeOfEmpty.getObjectArray<TestNestedData>(TestDataField.ARRAY, reader)
        assertEquals(0, empty.size)

        val jsonNodeWithSingleElementArray = reader.readTree(jsonStringWithSingleElementArray)
        val singleElement = jsonNodeWithSingleElementArray.getObjectArray<TestNestedData>(
            TestDataField.ARRAY,
            reader
        )
        assertEquals(1, singleElement.size)
        assertEquals(TestNestedData(-34534534543, "Single element."), singleElement[0])
    }

    @Test
    fun `test getOptObjectArray`() {
        // both ways will work
        val parser = reader.createParser(jsonString)
        val jsonNode = parser.codec.readTree<JsonNode>(parser)
        val nested = jsonNode.getOptObjectArray<TestNestedData>(TestDataField.ARRAY, parser.codec)
        assertEquals(expected.array, nested)

        val shouldBeNull = jsonNode.getOptObjectArray<TestNestedData>(
            TestDataField.OPTIONAL_ARRAY,
            parser.codec
        )
        assertNull(shouldBeNull)

        val jsonNodeOfEmptyString = reader.readTree(jsonStringWithEmptyStringForArray)
        val alsoNull = jsonNodeOfEmptyString.getOptObjectArray<TestNestedData>(
            TestDataField.ARRAY,
            reader
        )
        assertNull(alsoNull)
    }
}