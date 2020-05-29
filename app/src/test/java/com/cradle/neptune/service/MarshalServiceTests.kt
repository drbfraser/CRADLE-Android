package com.cradle.neptune.service

import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Settings
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

class MarshalServiceTests {

    @Mock
    private lateinit var settings: Settings

    @InjectMocks
    private lateinit var marshalService: MarshalService

    @BeforeEach
    fun injectMocks() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun unmarshalDatabaseJson() {
        val databaseJsonString = "{" +
            "\"bpDiastolic\":72," +
            "\"bpSystolic\":75," +
            "\"dateLastSaved\":\"1969-12-31T16:00:00-08:00[America/Los_Angeles]\"," +
            "\"dateRecheckVitalsNeeded\":\"1969-12-31T16:00:00-08:00[America/Los_Angeles]\"," +
            "\"dateTimeTaken\":\"2019-08-29T17:52:40-07:00[America/Los_Angeles]\"," +
            "\"dateUploadedToServer\":\"1969-12-31T16:00:00-08:00[America/Los_Angeles]\"," +
            "\"heartRateBPM\":65," +
            "\"isFlaggedForFollowup\":false," +
            "\"isImageUploaded\":false," +
            "\"manuallyChangeOcrResults\":-1," +
            "\"patient\":{" +
            "\"age\":35," +
            "\"dob\":\"null\"," +
            "\"drugHistoryList\":[]," +
            "\"gestationalAgeUnit\":\"GESTATIONAL_AGE_UNITS_WEEKS\"," +
            "\"gestationalAgeValue\":\"51\"," +
            "\"isPregnant\":true," +
            "\"medicalHistoryList\":[]," +
            "\"needAssessment\":true," +
            "\"patientId\":\"48300044415\"," +
            "\"patientName\":\"KD\"," +
            "\"patientSex\":\"FEMALE\"," +
            "\"villageNumber\":\"1004\"," +
            "\"zone\":\"null\"" +
            "}," +
            "\"readingId\":\"7c0d6a7f-7bcb-4b4b-a39f-ce8eea9e3bfb\"," +
            "\"referralComment\":\"null\"," +
            "\"symptoms\":[\"HEADACHE, ABDO PAIN, BLURRED VISION, BLEEDING\"]," +
            "\"totalOcrSeconds\":-1.0" +
            "}"

        val (patient, reading) = marshalService.unmarshalDatabaseJson(JsonObject(databaseJsonString))

        assertEquals(BloodPressure(75, 72, 65), reading.bloodPressure)
        assertEquals(
            parseDate("1969-12-31T16:00:00-08:00[America/Los_Angeles]"),
            reading.metadata.dateLastSaved
        )
        assertEquals(
            parseDate("1969-12-31T16:00:00-08:00[America/Los_Angeles]"),
            reading.dateRecheckVitalsNeeded
        )
        assertEquals(
            parseDate("2019-08-29T17:52:40-07:00[America/Los_Angeles]"),
            reading.dateTimeTaken
        )
        assertEquals(
            parseDate("1969-12-31T16:00:00-08:00[America/Los_Angeles]"),
            reading.metadata.dateUploadedToServer
        )
        assertEquals(false, reading.isFlaggedForFollowUp)
        assertEquals(false, reading.metadata.isImageUploaded)
        assertEquals(35, patient.age)
        assertNull(patient.dob)
        assertTrue(patient.drugHistoryList.isEmpty())
        assertEquals(GestationalAgeWeeks(51), patient.gestationalAge)
        assertTrue(patient.isPregnant)
        assertTrue(patient.medicalHistoryList.isEmpty())
        assertEquals("48300044415", patient.id)
        assertEquals(Sex.FEMALE, patient.sex)
        assertEquals("1004", patient.villageNumber)
        assertNull(patient.zone)
        assertEquals("7c0d6a7f-7bcb-4b4b-a39f-ce8eea9e3bfb", reading.id)
        assertNull(reading.referral)
        assertEquals(listOf("HEADACHE", "ABDO PAIN", "BLURRED VISION", "BLEEDING"), reading.symptoms)
        assertNull(reading.metadata.totalOcrSeconds)
    }

    @Test
    fun unmarshalDatabaseJson_isTheInverseOf_marshalToDatabaseJson() {
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

        val reading = Reading(
            "1234-abcd-5678-ef00",
            "5414842504",
            parseDate("2019-08-29T17:52:40-07:00[America/Los_Angeles]"),
            BloodPressure(110, 70, 65),
            UrineTest("+", "++", "-", "-", "-"),
            listOf("headache", "blurred vision", "pain"),
            Referral(parseDate("2019-08-29T17:52:40-07:00"), "HC101", "a comment"),
            null,
            parseDate("2019-08-29T17:52:40-07:00[America/Los_Angeles]"),
            true,
            listOf("1", "2", "3"),
            ReadingMetadata(
                "0.1.0-alpha",
                "some-info",
                parseDate("2019-08-29T17:52:40-07:00[America/Los_Angeles]"),
                parseDate("2019-08-29T17:52:40-07:00[America/Los_Angeles]"),
                null,
                false,
                null,
                null
            )
        )

        val json = marshalService.marshalToDatabaseJson(patient, reading)
        val (actualPatient, actualReading) = marshalService.unmarshalDatabaseJson(json)

        assertEquals(patient, actualPatient)
        assertEquals(reading, actualReading)
    }

    private fun parseDate(date: String) = ZonedDateTime.parse(
        date,
        DateTimeFormatter.ISO_ZONED_DATE_TIME.withZone(ZoneId.systemDefault())
    )
}
