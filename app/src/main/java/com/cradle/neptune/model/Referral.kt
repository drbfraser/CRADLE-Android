package com.cradle.neptune.model

import android.content.SharedPreferences
import com.cradle.neptune.ext.Field
import com.cradle.neptune.ext.booleanField
import com.cradle.neptune.ext.getIntOrNull
import com.cradle.neptune.ext.longField
import com.cradle.neptune.ext.optIntField
import com.cradle.neptune.ext.optStringField
import com.cradle.neptune.ext.put
import com.cradle.neptune.ext.stringField
import com.cradle.neptune.manager.LoginManager
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
data class Referral(
    val comment: String?,
    val healthFacilityName: String,
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
