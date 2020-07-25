package com.cradle.neptune.manager

import android.content.SharedPreferences
import com.cradle.neptune.model.Field
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Settings
import com.cradle.neptune.model.put
import com.cradle.neptune.view.LoginActivity
import javax.inject.Inject

/**
 * Provides custom marshalling methods tailored to specific API endpoint
 * formats.
 */
class MarshalManager @Inject constructor(
    private val settings: Settings,
    private val sharedPreferences: SharedPreferences
) {

    /**
     * Composes a [patient] and [reading] into a JSON format suitable to be
     * uploaded to the server.
     *
     * Aside from simply converting [patient] and [reading] to JSON, additional
     * information about the user and device are also included.
     *
     * @param patient the patient associated with [reading]
     * @param reading the reading to marshal
     */
    fun marshalToUploadJson(patient: Patient, reading: Reading): JsonObject = with(JsonObject()) {
        val readingJson = reading.marshal()
        val userId = sharedPreferences.getString(LoginActivity.USER_ID, null)
        readingJson.put(UploadJsonField.USER_ID, userId)

        put(UploadJsonField.PATIENT, patient.marshal())
        put(UploadJsonField.READING, readingJson)
        put(UploadJsonField.VHT_NAME, settings.vhtName)
        put(UploadJsonField.REGION, settings.region)
        put(UploadJsonField.OCR_ENABLED, settings.isOcrEnabled)
    }
}

/**
 * JSON fields when constructing JSON to upload to the server.
 */
private enum class UploadJsonField(override val text: String) : Field {
    PATIENT("patient"),
    READING("reading"),
    VHT_NAME("vhtName"),
    REGION("region"),
    OCR_ENABLED("ocrEnabled"),
    UPLOAD_IMAGES("uploadImages"),
    USER_ID("userId"),
}

/**
 * JSON fields when constructing JSON to store in the database.
 */
private enum class DatabaseJsonField(override val text: String) : Field {
    PATIENT("patient")
}
