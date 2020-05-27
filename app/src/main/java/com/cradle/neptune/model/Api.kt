package com.cradle.neptune.model

import android.os.AsyncTask
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

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
        for (i in 0..array.length()) {
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
        for (i in 0..jsonReadings.length()) {
            asyncReadings.add(async { Reading.unmarshal(jsonReadings.getJSONObject(i)) })
        }

        asyncReadings.map { it.await() }
    }

    return Pair(patient, readings)
}

/**
 * A legacy version of [unmarshalAllInfoArray] which runs on the IO dispatcher.
 *
 * Provides a [callback] parameter to execute some arbitrary code once the
 * array has been converted to models.
 *
 * The function is meant as a legacy interface between the old Java concurrency
 * model and Kotlin's coroutine model and should be removed at a later date.
 *
 * @param array the array to unmarshal
 * @param callback a callback to execute once finished
 */
fun legacyUnmarshallAllInfoAsync(array: JsonArray, callback: (List<Pair<Patient, List<Reading>>>) -> Unit) {
    legacyAsync<JsonArray, Unit> { params ->
        coroutineScope {
            val result = unmarshalAllInfoArray(params[0])
            callback(result)
        }
    }.execute(array)
}

/**
 * A legacy version of [unmarshalPatientAndReadings] which runs on the IO
 * dispatcher.
 *
 * The function is meant as a legacy interface between the old Java concurrency
 * model and Kotlin's coroutine model and should be removed at a later date.
 *
 * @param json the JSON to unmarshal
 * @param onOk a callback to execute once successfully constructing models
 * @param onError a callback to execute if an error occurred.
 */
fun legacyUnmarshallPatientAndReadings(
    json: JsonObject,
    onOk: (Pair<Patient, List<Reading>>) -> Unit,
    onError: (Exception) -> Unit
) {
    legacyAsync<JsonObject, Unit> { params ->
        coroutineScope {
            try {
                val result = unmarshalPatientAndReadings(params[0])
                onOk(result)
            } catch (e: Exception) {
                onError(e)
            }
        }
    }.execute(json)
}

/**
 * Wraps a `suspend` function in an [AsyncTask] which executes the contents
 * of [job] using the IO dispatcher as to not interrupt the UI thread.
 *
 * The function is meant as a legacy interface between the old Java concurrency
 * model and Kotlin's coroutine model and should be removed at a later date.
 *
 * @param job the job to execute
 * @return a legacy [AsyncTask] object for the job
 */
private fun <Params, T> legacyAsync(job: suspend (Array<out Params>) -> T): AsyncTask<Params, Void, T> {
    return object : AsyncTask<Params, Void, T>() {
        override fun doInBackground(vararg params: Params): T {
            // Run the actual job on Kotlin's dispatcher. The only work that
            // this task does is wait for the job to complete.
            return runBlocking(Dispatchers.IO) { job(params) }
        }
    }
}

/**
 * Fields specific to marshalling responses to the `/api/patient/allinfo`
 * API request.
 */
private enum class AllInfoFields(override val text: String) : Field {
    READINGS("readings")
}
