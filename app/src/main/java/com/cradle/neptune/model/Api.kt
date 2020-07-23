package com.cradle.neptune.model

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Asynchronously converts a [JsonArray] containing patient's paired with their
 * readings into model objects.
 *
 * @param array a JSON array containing patient info (for example: from a
 * request to `/api/patient/allinfo`)
 * @return patient and reading data as models
 * @throws JsonException if [array] does not contain the required data
 */
suspend fun unmarshalAllInfoArray(array: JsonArray): List<Pair<Patient, List<Reading>>> {
    return coroutineScope {
        val asyncInfo = mutableListOf<Deferred<Pair<Patient, List<Reading>>>>()
        for (i in 0 until array.length()) {
            asyncInfo.add(async { unmarshalPatientAndReadings(array.getJSONObject(i)) })
        }

        asyncInfo.map { it.await() }
    }
}

/**
 * Asynchronously converts a [JsonObject] containing a a patient paired with
 * all of it's readings into [Patient] and [Reading] objects.
 *
 * In an effort to improve performance, the "readings" JSON array is
 * unmarshalled in parallel.
 *
 * @param json a JSON object containing a single patient and a list of its
 * readings
 * @return the patient and its readings as models
 * @throws JsonException if [json] does not contain the required data
 */
suspend fun unmarshalPatientAndReadings(json: JsonObject): Pair<Patient, List<Reading>> {
    // Parse patient info.
    val patient = Patient.unmarshal(json)

    // Asynchronously parse the patient's readings.
    val readings = coroutineScope {
        val jsonReadings = json.arrayField(AllInfoFields.READINGS)
        val asyncReadings = mutableListOf<Deferred<Reading>>()
        for (i in 0 until jsonReadings.length()) {
            asyncReadings.add(async { Reading.unmarshal(jsonReadings.getJSONObject(i)) })
        }

        asyncReadings.map { it.await() }
    }

    return Pair(patient, readings)
}

/**
 * Fields specific to marshalling responses to the `/api/patient/allinfo`
 * API request.
 */
private enum class AllInfoFields(override val text: String) : Field {
    READINGS("readings")
}
