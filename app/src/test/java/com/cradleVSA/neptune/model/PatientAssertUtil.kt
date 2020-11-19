package com.cradleVSA.neptune.model

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Asserts that two [Patient] objects in JSON string form are equal in terms of the JSON structure
 *
 * The given strings can be either strings for a serialized [Patient] or for a serialized
 * [PatientAndReadings] object.
 */
fun assertPatientJsonEqual(jsonStringForExpected: String, jsonStringForActual: String) {
    // Note: jsonStringForExpected is taken from the server, so it has fields
    // that the Android app ignores.
    val expectedJson = JSONObject(jsonStringForExpected).apply {
        // can be deserializing from a PatientAndReadings JSON string, but we want to
        // compare the patients that are coming from it. Remove the array of readings.
        remove("readings")
        // This isn't used by the Android app, so it's not written during serialization.
        remove("created")
        // This isn't used by the Android app, so it's not written during serialization.
        remove("userId")
    }
    val actualJson = JSONObject(jsonStringForActual).apply {
        remove("readings")
    }
    assertEquals(expectedJson.length(), actualJson.length()) {
        "number of keys differ from expected\n" +
            "expected JSON: $expectedJson\n" +
            "actual JSON: $actualJson"
    }
    expectedJson.keys().forEach {
        assertEquals(expectedJson.get(it).toString(), actualJson.get(it).toString()) {
            "key $it was different"
        }
    }
}
