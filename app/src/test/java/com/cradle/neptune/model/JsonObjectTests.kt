package com.cradle.neptune.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

class JsonObjectTests {
    @Test
    fun jsonObject_unionInlinesJson() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"b":2}""")
        a.union(b)

        assertEquals(1, a.get("a"))
        assertEquals(2, a.get("b"))
        assertEquals("world", a.get("hello"))
    }

    @Test
    fun jsonObject_unionOverwritesExisting() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"a":2}""")
        a.union(b)

        assertEquals(2, a.get("a"))
        assertEquals("world", a.get("hello"))
    }
}
