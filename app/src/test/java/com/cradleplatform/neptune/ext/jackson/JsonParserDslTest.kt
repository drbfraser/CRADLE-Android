package com.cradleplatform.neptune.ext.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class JsonParserDslTest {

    private data class Item(val id: Int)

    private data class Parsed(val items: List<Item>, val errors: JsonNode?)

    private val reader = jacksonObjectMapper().readerFor(Item::class.java)

    /**
     * Parses [json] the way the sync response parsers do: `items` and `errors` are the only fields
     * that are handled.
     */
    private fun parse(json: String): Parsed {
        val items = mutableListOf<Item>()
        var errors: JsonNode? = null
        reader.createParser(json).use { parser ->
            parser.parseObject {
                when (currentName) {
                    "items" -> parseObjectArray<Item>(reader) { items.add(it) }
                    "errors" -> {
                        nextToken()
                        errors = readValueAsTree()
                    }
                }
            }
        }
        return Parsed(items, errors)
    }

    @Test
    fun `parseObject skips an unhandled array of objects that comes before a handled field`() {
        val parsed = parse(
            """
            {
                "unknown": [
                    {"readingId": "abc-123", "errors": "Not authorized to sync this patient."},
                    {"readingId": "def-456", "errors": {"nested": [1, 2]}}
                ],
                "items": [{"id": 1}, {"id": 2}]
            }
            """.trimIndent()
        )

        assertEquals(listOf(Item(1), Item(2)), parsed.items)
    }

    @Test
    fun `parseObject skips unhandled values and nested objects`() {
        val parsed = parse(
            """
            {
                "count": 3,
                "meta": {"items": [{"id": 99}], "more": {"errors": "ignored"}},
                "items": [{"id": 1}],
                "after": null
            }
            """.trimIndent()
        )

        assertEquals(listOf(Item(1)), parsed.items)
        assertEquals(null, parsed.errors)
    }

    @Test
    fun `parseObject still reads every handled field`() {
        val parsed = parse(
            """
            {
                "errors": [{"readingId": "abc-123", "errors": "Some message."}],
                "items": [{"id": 1}, {"id": 2}, {"id": 3}]
            }
            """.trimIndent()
        )

        assertEquals(listOf(Item(1), Item(2), Item(3)), parsed.items)
        assertEquals("abc-123", parsed.errors?.get(0)?.get("readingId")?.asText())
    }

    @Test
    fun `parseObject reads an object with no unhandled fields as before`() {
        val parsed = parse("""{"items": [{"id": 7}]}""")

        assertEquals(listOf(Item(7)), parsed.items)
        assertEquals(null, parsed.errors)
    }
}
