package com.cradleVSA.neptune.model

import android.content.SharedPreferences
import com.cradleVSA.neptune.ext.Field
import com.cradleVSA.neptune.ext.booleanField
import com.cradleVSA.neptune.ext.getIntOrNull
import com.cradleVSA.neptune.ext.longField
import com.cradleVSA.neptune.ext.optIntField
import com.cradleVSA.neptune.ext.optStringField
import com.cradleVSA.neptune.ext.put
import com.cradleVSA.neptune.ext.stringField
import com.cradleVSA.neptune.manager.LoginManager
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

/**
 * Holds information about a referral.
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
data class Referral(
    val comment: String?,
    @JsonProperty("referralHealthFacilityName") val healthFacilityName: String,
    val dateReferred: Long,
    val id: Int?,
    val userId: Int?,
    val patientId: String,
    val readingId: String,
    var isAssessed: Boolean
) : Marshal<JSONObject>, Serializable {

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
        userId = sharedPreferences.getIntOrNull(LoginManager.USER_ID_KEY),
        patientId = patientId,
        readingId = readingId,
        isAssessed = false
    )

    override fun marshal(): JSONObject = with(JSONObject()) {
        put(ReferralField.ID, id)
        put(ReferralField.DATE_REFERRED, dateReferred)
        put(ReferralField.COMMENT, comment)
        put(ReferralField.USER_ID, userId)
        put(ReferralField.PATIENT_ID, patientId)
        put(ReferralField.HEALTH_FACILITY_NAME, healthFacilityName)
        put(ReferralField.READING_ID, readingId)
        put(ReferralField.IS_ASSESSED, isAssessed)
    }

    companion object :
        Unmarshal<Referral, JSONObject> {
        /**
         * Constructs a [Referral] object from a [JSONObject].
         *
         * @throws JSONException if any of the required fields are missing
         */
        override fun unmarshal(data: JSONObject) =
            Referral(
                id = data.optIntField(ReferralField.ID),
                dateReferred = data.longField(ReferralField.DATE_REFERRED),
                comment = data.optStringField(ReferralField.COMMENT),
                userId = data.optIntField(ReferralField.USER_ID),
                patientId = data.stringField(ReferralField.PATIENT_ID),
                healthFacilityName = data.stringField(ReferralField.HEALTH_FACILITY_NAME),
                readingId = data.stringField(ReferralField.READING_ID),
                isAssessed = data.booleanField(ReferralField.IS_ASSESSED)
            )
    }
}

/**
 * The information that is expected by the SMS relay app.
 * TODO: Ensure that removing "referralId" doesn't break the SMS relay app.
 */
data class SmsReferral(val referralId: String, val patient: PatientAndReadings)

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
