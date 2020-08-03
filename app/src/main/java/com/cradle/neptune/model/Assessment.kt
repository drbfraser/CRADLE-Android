package com.cradle.neptune.model

import java.io.Serializable

/**
 * Data about an assessment made by a health care worker for a reading.
 *
 * Note that this object is sometimes also referred to as "follow up".
 *
 * @property id A unique id for this follow up populated by the server
 * @property dateAssessed The time this follow up was made as a unix timestamp
 * @property healthCareWorkerId The id of the user who made this assessment
 * @property readingId Id of the reading this follow up belongs to
 * @property diagnosis An optional medical diagnosis
 * @property treatment An optional treatment description
 * @property medicationPrescribed An optional description of the medication
 *  prescribed to the patient
 * @property followupNeeded True if a follow up is required by the VHT
 * @property followupInstructions Instructions for the follow up if required
 */
data class Assessment(
    val id: Int?,
    val dateAssessed: Long,
    val healthCareWorkerId: Int,
    val readingId: String,
    val diagnosis: String?,
    val treatment: String?,
    val medicationPrescribed: String?,
    val specialInvestigations: String?,
    val followupNeeded: Boolean,
    val followupInstructions: String?
) : Marshal<JsonObject>, Serializable {

    /**
     * Converts this object into a [JsonObject].
     */
    override fun marshal() = with(JsonObject()) {
        put(AssessmentField.ID, id)
        put(AssessmentField.DATE_ASSESSED, dateAssessed)
        put(AssessmentField.HEALTH_CARE_WORKER_ID, healthCareWorkerId)
        put(AssessmentField.READING_ID, readingId)
        put(AssessmentField.DIAGNOSIS, diagnosis)
        put(AssessmentField.TREATMENT, treatment)
        put(AssessmentField.MEDICATION_PRESCRIBED, medicationPrescribed)
        put(AssessmentField.SPECIAL_INVESTIGATIONS, specialInvestigations)
        put(AssessmentField.FOLLOW_UP_NEEDED, followupNeeded)
        put(AssessmentField.FOLLOW_UP_INSTRUCTIONS, followupInstructions)
    }

    companion object :
        Unmarshal<Assessment, JsonObject> {
        /**
         * Constructs a new instance of this class from a [JsonObject].
         */
        override fun unmarshal(data: JsonObject) =
            Assessment(
                id = data.optIntField(AssessmentField.ID),
                dateAssessed = data.longField(AssessmentField.DATE_ASSESSED),
                healthCareWorkerId = data.intField(AssessmentField.HEALTH_CARE_WORKER_ID),
                readingId = data.stringField(AssessmentField.READING_ID),
                diagnosis = data.optStringField(AssessmentField.DIAGNOSIS),
                treatment = data.optStringField(AssessmentField.TREATMENT),
                medicationPrescribed = data.optStringField(AssessmentField.MEDICATION_PRESCRIBED),
                specialInvestigations = data.optStringField(AssessmentField.SPECIAL_INVESTIGATIONS),
                followupNeeded = data.optBooleanField(AssessmentField.FOLLOW_UP_NEEDED),
                followupInstructions = data.optStringField(AssessmentField.FOLLOW_UP_INSTRUCTIONS)
            )
    }
}

private enum class AssessmentField(override val text: String) : Field {
    ID("id"),
    DATE_ASSESSED("dateAssessed"),
    HEALTH_CARE_WORKER_ID("healthCareWorkerId"),
    READING_ID("readingId"),
    DIAGNOSIS("diagnosis"),
    TREATMENT("treatment"),
    MEDICATION_PRESCRIBED("medicationPrescribed"),
    SPECIAL_INVESTIGATIONS("specialInvestigations"),
    FOLLOW_UP_NEEDED("followupNeeded"),
    FOLLOW_UP_INSTRUCTIONS("followupInstructions"),
}
