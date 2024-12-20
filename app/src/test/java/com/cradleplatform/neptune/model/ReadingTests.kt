package com.cradleplatform.neptune.model

import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ReadingTests {
    @Test
    fun `jackson serialize And Deserialize for Dataclass Reading`() {
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
            referralHealthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            id = "345",
            userId = 2,
            isAssessed = false,
            actionTaken = null,
            cancelReason = null,
            isCancelled = false,
            lastEdited = 0L,
            notAttendReason = null,
            notAttended = false
        )

        val assessmentForReading = Assessment(
            id = "4535",
            dateAssessed = 1595745946L,
            healthcareWorkerId = 2,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followUpNeeded = true, followUpInstructions = "These are things to do",
            patientId = patientId
        )

        return Reading(
            id = readingId,
            patientId = patientId,
            dateTaken = unixTime,
            lastEdited = unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            urineTest = UrineTest("+", "++", "-", "NAD", "NAD"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = referralForReading,
            followUp = assessmentForReading,
            dateRetestNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            isUploadedToServer = false,
            userId = 3
        )
    }
}
