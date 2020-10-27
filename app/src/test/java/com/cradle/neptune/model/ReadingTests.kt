package com.cradle.neptune.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReadingTests {
    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val unixTime: Long = 1595645893
        val reading = Reading(
            id = "1234-abcd-5678-ef00", "5414842504", unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            respiratoryRate = 50,
            oxygenSaturation = 65,
            temperature = 34,
            urineTest = UrineTest("+", "++", "-", "-", "-"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = null,
            followUp = null,
            dateRecheckVitalsNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            metadata = ReadingMetadata(),
            isUploadedToServer = false
        )

        val json = reading.marshal()
        val actual = unmarshal(Reading, json)
        assertEquals(reading, actual)
    }
}
