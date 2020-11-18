package com.cradleVSA.neptune.model

import com.cradleVSA.neptune.ext.map
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PatientAndReadingsTests {
    @Test
    fun `test single unmarshal without gestational age`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientNoGestAgeJsonAndExpected

        val jsonObject = JSONObject(jsonString)
        val patientAndReadingsUnmarshal = PatientAndReadings.unmarshal(jsonObject)
        assertEquals(expected.patient, patientAndReadingsUnmarshal.patient)
        assertEquals(expected.readings, patientAndReadingsUnmarshal.readings)
    }

    @Test
    fun `test single unmarshal with gestational age`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientWithGestAgeJsonAndExpected

        val jsonObject = JSONObject(jsonString)
        val patientAndReadingsUnmarshal = PatientAndReadings.unmarshal(jsonObject)
        assertEquals(expected.patient, patientAndReadingsUnmarshal.patient)
        assertEquals(expected.readings, patientAndReadingsUnmarshal.readings)
    }

    @Test
    fun `test single unmarshal with referral and follow up`() {
        val (jsonString, expected) = CommonPatientReadingJsons.patientWithReferralAndFollowup

        val jsonObject = JSONObject(jsonString)
        val patientAndReadingsUnmarshal = PatientAndReadings.unmarshal(jsonObject)
        assertEquals(expected.patient, patientAndReadingsUnmarshal.patient)

        val expectedEmptyList = expected.readings[0].previousReadingIds
        val unmarshaledEmptyList = patientAndReadingsUnmarshal.readings[0].previousReadingIds

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

        assertEquals(expected.readings, patientAndReadingsUnmarshal.readings)
    }

    @Test
    fun `test the unmarshalling`() {
        // Check that the JSON and the PatientsAndReadings object can actually unmarshal
        // and be parsed correctly.
        val jsonArray = JSONArray(CommonPatientReadingJsons.allPatientsJsonExpectedPair.first)
        val unmarshalPatientAndReadings = jsonArray.map(
            JSONArray::getJSONObject,
            PatientAndReadings.Companion::unmarshal
        )

        unmarshalPatientAndReadings.forEachIndexed { outerIdx, unmarshaledPatientAndReadings ->
            val (unmarshalPatient, unmarshalReadings) =
                unmarshaledPatientAndReadings.patient to unmarshaledPatientAndReadings.readings

            CommonPatientReadingJsons.allPatientsJsonExpectedPair.second.find {
                it.patient.id == unmarshalPatient.id
            }?.let { expectedPatientAndReadings ->
                assertEquals(expectedPatientAndReadings.patient, unmarshalPatient) {
                    "failed at outerIdx $outerIdx, "
                }
                assertEquals(expectedPatientAndReadings.readings, unmarshalReadings)
            } ?: error("fail")
        }
    }
}