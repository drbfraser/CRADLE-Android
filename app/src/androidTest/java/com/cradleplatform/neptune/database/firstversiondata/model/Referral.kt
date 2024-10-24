package com.cradleplatform.neptune.database.firstversiondata.model

import android.content.SharedPreferences
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.viewmodel.UserViewModel
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

/**
 * Holds information about a referral.
 * DO NOT EDIT
 *
 * @property comment An optional comment made by the user about this referral
 * @property healthFacilityName The name of the health facility this referral
 *  is being made to
 * @property dateReferred The time at which this referral was made as a unix
 *  timestamp
 * @property id The unique identifier for this referral assigned by the server
 * @property userId The id of the user who made this referral
 * @property patientId The id of patient being referred
 * @property readingId The id of the reading being referred
 * @property isAssessed True if the referral has been assessed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Referral constructor(
    @JsonProperty("comment")
    val comment: String?,
    @JsonProperty("referralHealthFacilityName")
    val healthFacilityName: String,
    @JsonProperty("dateReferred")
    val dateReferred: Long,
    @JsonProperty("id")
    val id: Int?,
    @JsonProperty("userId")
    val userId: Int?,
    @JsonProperty("patientId")
    val patientId: String,
    @JsonProperty("readingId")
    val readingId: String,
    @JsonProperty("isAssessed")
    var isAssessed: Boolean
) : Serializable {

    constructor(
        comment: String?,
        healthFacilityName: String,
        dateReferred: Long,
        patientId: String,
        readingId: String,
        sharedPreferences: SharedPreferences
    ) : this(
        comment = comment,
        healthFacilityName = healthFacilityName,
        dateReferred = dateReferred,
        id = null,
        userId = sharedPreferences.getIntOrNull(UserViewModel.USER_ID_KEY),
        patientId = patientId,
        readingId = readingId,
        isAssessed = false
    )
}

/**
 * The information that is expected by the SMS relay app.
 */
internal data class SmsReferral(val referralId: String, val patient: PatientAndReadings)

/**
 * JSON keys for [Referral] fields.
 */
private enum class ReferralField(override val text: String) : Field {
    ID("id"),
    DATE_REFERRED("dateReferred"),
    COMMENT("comment"),
    USER_ID("userId"),
    PATIENT_ID("patientId"),
    HEALTH_FACILITY_NAME("referralHealthFacilityName"),
    READING_ID("readingId"),
    IS_ASSESSED("isAssessed")
}
