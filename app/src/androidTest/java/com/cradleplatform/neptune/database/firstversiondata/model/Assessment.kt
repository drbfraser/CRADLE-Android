package com.cradleplatform.neptune.database.firstversiondata.model

import com.cradleplatform.neptune.ext.Field
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
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
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Assessment(
    val id: Int?,
    val dateAssessed: Long,
    @JsonProperty("healthcareWorkerId")
    val healthCareWorkerId: Int,
    val readingId: String,
    val diagnosis: String?,
    val treatment: String?,
    val medicationPrescribed: String?,
    val specialInvestigations: String?,
    val followupNeeded: Boolean,
    val followupInstructions: String?
) : Serializable

private enum class AssessmentField(override val text: String) : Field {
    ID("id"),
    DATE_ASSESSED("dateAssessed"),
    HEALTH_CARE_WORKER_ID("healthcareWorkerId"),
    READING_ID("readingId"),
    DIAGNOSIS("diagnosis"),
    TREATMENT("treatment"),
    MEDICATION_PRESCRIBED("medicationPrescribed"),
    SPECIAL_INVESTIGATIONS("specialInvestigations"),
    FOLLOW_UP_NEEDED("followupNeeded"),
    FOLLOW_UP_INSTRUCTIONS("followupInstructions"),
}
