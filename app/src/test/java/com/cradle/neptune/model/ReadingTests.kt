package com.cradle.neptune.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

class ReadingTests {
    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val unixTime:Long =1595645893
        val reading = Reading("1234-abcd-5678-ef00", "5414842504", unixTime,
            BloodPressure(110, 70, 65),
            UrineTest("+", "++", "-", "-", "-"),
            listOf("headache", "blurred vision", "pain"),
            Referral(unixTime, "HC101", "a comment"),
            null,
            unixTime,
            true,
            emptyList(),
            ReadingMetadata(),true
        )

        val json = reading.marshal()
        val actual = unmarshal(Reading, json)
        assertEquals(reading, actual)
    }
}
