package com.cradle.neptune.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PatientTests {
    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val patient = Patient(
            "5414842504",
            "AB",
            null,
            32,
            GestationalAgeWeeks(28),
            Sex.FEMALE,
            true,
            null,
            null,
            emptyList(),
            emptyList()
        )

        val json = patient.marshal()
        val actual = unmarshal(Patient, json)
        assertEquals(patient, actual)
    }

    @Test
    fun marshal_patientWithReadings_alsoMarshalsReadings() {
        val patientId = "2345452362"
        val makeReading = { id: String ->
            Reading(
                id = id,
                patientId = patientId,
                dateTimeTaken = 123,
                bloodPressure = BloodPressure(110, 80, 70),
                symptoms = emptyList(),
                referral = null,
                followUp = null,
                urineTest = null,
                dateRecheckVitalsNeeded = null,
                isFlaggedForFollowUp = false
            )
        }

        val patient = Patient(
            id = patientId,
            name = "AB",
            dob = null,
            age = 32,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        val patientAndReadings = PatientAndReadings(patient, listOf("a", "b", "c").map(makeReading))

        val json = patientAndReadings.marshal()
        assertTrue(json.has("readings"))
        assertEquals(3, json.getJSONArray("readings").length())
    }
}
