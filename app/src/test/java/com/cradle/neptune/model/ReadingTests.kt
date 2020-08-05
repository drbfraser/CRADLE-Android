package com.cradle.neptune.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReadingTests {
    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val unixTime:Long =1595645893
        val reading = Reading("1234-abcd-5678-ef00", "5414842504", unixTime,
            BloodPressure(110, 70, 65),
            UrineTest("+", "++", "-", "-", "-"),
            listOf("headache", "blurred vision", "pain"),
            Referral("a comment", "HC0000", 5, "123", "abc"),
            null,
            unixTime,
            true,
            listOf("1", "2", "3"),
            ReadingMetadata(),
            false
        )

        val json = reading.marshal()
        val actual = unmarshal(Reading, json)
        assertEquals(reading, actual)
    }
}
