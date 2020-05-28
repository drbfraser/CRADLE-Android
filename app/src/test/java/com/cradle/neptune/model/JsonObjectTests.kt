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

    @Test
    fun jsonObject_ifDateIsEpochSeconds_parseIt() {
        val time: Long = 1590701531
        val a = JsonObject("""{"date":$time}""")

        val instant = Instant.ofEpochSecond(time)
        val expected = ZonedDateTime.ofInstant(instant, ZoneId.of("America/Los_Angeles"))
        assertEquals(expected, a.dateField(Field.fromString("date")))
    }

    @Test
    fun jsonObject_ifDateContainsTimeZoneName_parseIt() {
        val time = "2019-08-29T17:52:40-07:00[America/Los_Angeles]"
        val a = JsonObject("""{"date":"$time"}""")

        val expected = ZonedDateTime.parse(
            "2019-08-29T17:52:40-07:00[America/Los_Angeles]",
            DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.of("America/Los_Angeles"))
        )
        assertEquals(expected, a.dateField(Field.fromString("date")))
    }
}
