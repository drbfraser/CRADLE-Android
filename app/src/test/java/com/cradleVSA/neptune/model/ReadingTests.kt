package com.cradleVSA.neptune.model

import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ReadingTests {
    @Test
    fun reading_jackson_serializeAndDeserialize() {
        val reading = createTestReading()

        val writer = JacksonMapper.writerForReading
        val serialized = writer.writeValueAsString(reading)

        val reader = JacksonMapper.readerForReading
        val deserializedReading = reader.readValue<Reading>(serialized)

        assertEquals(reading, deserializedReading)
    }

    private fun createTestReading(): Reading {
        val unixTime: Long = 1595645893
        val patientId = "5414842504"
        val readingId = UUID.randomUUID().toString()
        val referralForReading = Referral(
            comment = "This is a comment",
            healthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            readingId = readingId,
            id = 345,
            userId = 2,
            isAssessed = true
        )
        val assessmentForReading = Assessment(
            id = 4535,
            dateAssessed = 1595745946L,
            healthCareWorkerId = 2,
            readingId = readingId,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followupNeeded = true, followupInstructions = "These are things to do"
        )

        return Reading(
            id = readingId,
            patientId = patientId,
            dateTimeTaken = unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            urineTest = UrineTest("+", "++", "-", "NAD", "NAD"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = referralForReading,
            followUp = assessmentForReading,
            dateRecheckVitalsNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            metadata = ReadingMetadata(),
            isUploadedToServer = false
        )
    }
}
