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
            listOf("drug-1", "drug-2", "drug-3"),
            listOf("hist-1", "hist-2", "hist-3")
        )

        val json = patient.marshal()
        val actual = unmarshal(Patient, json)
        assertEquals(patient, actual)
    }
}
