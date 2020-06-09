package com.cradle.neptune.viewmodel

import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.threeten.bp.ZonedDateTime

class PatientReadingViewModelTests {

    /**
     * This test runs through the process of constructing the patient and
     * reading models when the user has supplied input for each field in
     * the view model when constructing a new reading with no existing
     * patient/reading.
     */
    @Test
    fun modelConstruction_emulateUserInput_allFields() {
        val model = PatientReadingViewModel()
        model.patientId = "123456"
        model.patientName = "foo"
        model.patientDob = "1990-01-01"
        model.patientSex = Sex.FEMALE
        model.patientZone = "1"
        model.patientVillageNumber = "123"
        model.patientIsPregnant = true
        model.patientGestationalAge = GestationalAgeWeeks(12)
        model.symptoms = listOf("Headache", "Unwell", "Some Other Symptom")
        model.urineTest = UrineTest("++", "+", "-", "-", "+++")
        model.bloodPressure = BloodPressure(110, 70, 65)
        model.isFlaggedForFollowUp = true

        // Emulate the clicking the save button which injects the current time
        // into the dateTimeTaken field.
        val time = ZonedDateTime.now()
        model.dateTimeTaken = time

        val (patient, reading) = model.constructModels()

        assertEquals("123456", patient.id)
        assertEquals("foo", patient.name)
        assertEquals("1990-01-01", patient.dob)
        assertNull(patient.age)
        assertEquals(Sex.FEMALE, patient.sex)
        assertEquals("1", patient.zone)
        assertEquals("123", patient.villageNumber)
        assertTrue(patient.isPregnant)
        assertEquals(GestationalAgeWeeks(12), patient.gestationalAge)
        assertEquals(listOf("Headache", "Unwell", "Some Other Symptom"), reading.symptoms)
        assertEquals(UrineTest("++", "+", "-", "-", "+++"), reading.urineTest)
        assertEquals(BloodPressure(110, 70, 65), reading.bloodPressure)
        assertTrue(reading.isFlaggedForFollowUp)
        assertEquals(time, reading.dateTimeTaken)

        // Implicit fields
        assertEquals("123456", reading.patientId)
        assertTrue(reading.previousReadingIds.isEmpty())
        assertNull(reading.referral)
    }
}