package com.cradle.neptune.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

class ReadingTests {
    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val date = ZonedDateTime.parse(
            "2019-08-29T17:52:40-07:00",
            DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.systemDefault())
        )
        val reading = Reading(
            "1234-abcd-5678-ef00",
            "5414842504",
            date,
            BloodPressure(110, 70, 65),
            UrineTest("+", "++", "-", "-", "-"),
            listOf("headache", "blurred vision", "pain"),
            Referral(date, "HC101", "a comment"),
            null,
            date,
            true,
            listOf("1", "2", "3"),
            ReadingMetadata(
                "0.1.0-alpha",
                "some-info",
                date,
                date,
                null,
                false,
                null,
                null
            )
        )

        val json = reading.marshal()
        val actual = unmarshal(Reading, json)
        assertEquals(reading, actual)
    }
}
