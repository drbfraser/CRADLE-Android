package com.cradle.neptune.service

import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.Settings
import javax.inject.Inject

/**
 * Provides custom marshalling methods tailored to specific API endpoint
 * formats.
 */
class MarshalService @Inject constructor(private val settings: Settings) {

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
    fun marshalToUploadJson(patient: Patient, reading: Reading) = with(JsonObject()) {
        put("patient", patient.marshal())
        put("reading", reading.marshal())
        put("vhtName", settings.vhtName)
        put("region", settings.region)
        put("ocrEnabled", settings.ocrEnabled)
        put("uploadImages", settings.shouldUploadImages())
    }
}
