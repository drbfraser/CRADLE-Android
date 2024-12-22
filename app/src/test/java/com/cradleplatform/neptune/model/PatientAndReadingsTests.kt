package com.cradleplatform.neptune.model

import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.fasterxml.jackson.module.kotlin.readValue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PatientAndReadingsTests {
    @Test
    fun `test single deserialize without gestational age`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientNoGestAgeJsonAndExpected

        val reader = JacksonMapper.createReader<PatientAndReadings>()
        val deserialize = reader.readValue<PatientAndReadings>(jsonString)
        assertEquals(expected.patient, deserialize.patient)
        assertEquals(expected.readings, deserialize.readings)

        val writer = JacksonMapper.createWriter<PatientAndReadings>()
        val serialized = writer.writeValueAsString(deserialize)

        assertPatientJsonEqual(
            jsonStringForExpected = jsonString,
            jsonStringForActual = serialized
        )
    }

    @Test
    fun `test single unmarshal with gestational age`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientWithGestAgeJsonAndExpected

        val reader = JacksonMapper.createReader<PatientAndReadings>()
        val deserialize = reader.readValue<PatientAndReadings>(jsonString)
        assertEquals(expected.patient, deserialize.patient)
        assertEquals(expected.readings, deserialize.readings)

        val writer = JacksonMapper.createWriter<PatientAndReadings>()
        val serialized = writer.writeValueAsString(deserialize)

        assertPatientJsonEqual(
            jsonStringForExpected = jsonString,
            jsonStringForActual = serialized
        )
    }

    @Test
    fun `test single unmarshal with referral and follow up`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientWithReferralAndFollowup

        val reader = JacksonMapper.createReader<PatientAndReadings>()
        val deserialize = reader.readValue<PatientAndReadings>(jsonString)
        assertEquals(expected.patient, deserialize.patient)

        val expectedEmptyList = expected.readings[0].previousReadingIds
        val unmarshaledEmptyList = deserialize.readings[0].previousReadingIds

        assert(expectedEmptyList.isEmpty()) {
            "expected empty but it has ${expectedEmptyList.size} elements." +
                " Is the element an empty string? ${expectedEmptyList[0] == ""}"
        }
        assert(unmarshaledEmptyList.isEmpty()) {
            "expected empty but  has ${unmarshaledEmptyList.size} elements." +
                " Is the element an empty string? ${unmarshaledEmptyList[0] == ""}"
        }
        assertEquals(
            expectedEmptyList,
            unmarshaledEmptyList,
        ) { "what? they're both empty but they're not equal?" }

        assertEquals(expected.readings, deserialize.readings)

        val writer = JacksonMapper.createWriter<PatientAndReadings>()
        val serialized = writer.writeValueAsString(deserialize)

        assertPatientJsonEqual(
            jsonStringForExpected = jsonString,
            jsonStringForActual = serialized
        )
    }

    @Test
    fun `test the deserialization of JSONArray of PatientAndReadings`() {
        val reader = JacksonMapper.readerForPatientAndReadings
        val jsonArrayAsString = CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
        val deserialized = reader.readValues<PatientAndReadings>(jsonArrayAsString)

        deserialized.forEach { deserializedPatientAndReadings ->
            val (deserializedPatient, deserializedReadings) =
                deserializedPatientAndReadings.patient to deserializedPatientAndReadings.readings

            CommonPatientReadingJsons.allPatientsJsonExpectedPair.second.find {
                it.patient.id == deserializedPatient.id
            }?.let { expectedPatientAndReadings ->
                assertEquals(expectedPatientAndReadings.patient, deserializedPatient)
                assertEquals(expectedPatientAndReadings.readings, deserializedReadings)
            } ?: error("fail")

            val asString = JacksonMapper.mapper.writeValueAsString(deserializedPatientAndReadings)
            val parsedPatientAndReadings = JacksonMapper.mapper.readValue<PatientAndReadings>(asString)
            assertEquals(deserializedPatientAndReadings, parsedPatientAndReadings)
        }
    }
}