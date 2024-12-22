package com.cradleplatform.neptune.database.firstversiondata.model

import android.content.SharedPreferences
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.viewmodel.UserViewModel
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable
import java.util.UUID

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
 * @property actionTaken The action taken
 * @property cancelReason The reason of cancel
 * @property notAttendReason The reason of not attend
 * @property isAssessed True if the referral has been assessed
 * @property isCancelled True if the referral has been cancelled
 * @property notAttended True if the referral has not been attended
 * @property lastEdited Last time referral was edited on android
 * @property lastServerUpdate Last time the referral has gotten updated from the server.
 */
@Entity(
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["patientId"]),
        Index(value = ["healthFacilityName"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("patientId"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HealthFacility::class,
            parentColumns = arrayOf("name"),
            childColumns = arrayOf("healthFacilityName"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class Referral (
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    var id: String = UUID.randomUUID().toString(),

    @ColumnInfo @JsonProperty("comment")
    var comment: String?,

    @ColumnInfo @JsonProperty("healthFacilityName")
    var healthFacilityName: String,

    @ColumnInfo @JsonProperty("dateReferred")
    var dateReferred: Long,

    @ColumnInfo @JsonProperty("userId")
    var userId: Int?,

    @ColumnInfo @JsonProperty("patientId")
    var patientId: String,

    @ColumnInfo @JsonProperty("actionTaken")
    var actionTaken: String?,

    @ColumnInfo @JsonProperty("cancelReason")
    var cancelReason: String?,

    @ColumnInfo @JsonProperty("notAttendReason")
    var notAttendReason: String?,

    @ColumnInfo @JsonProperty("isAssessed")
    var isAssessed: Boolean,

    @ColumnInfo @JsonProperty("isCancelled")
    var isCancelled: Boolean,

    @ColumnInfo @JsonProperty("notAttended")
    var notAttended: Boolean,

    @ColumnInfo @JsonProperty("lastEdited")
    var lastEdited: Long,

    @ColumnInfo @JsonProperty("lastServerUpdate")
    var lastServerUpdate: Long? = null,

    @ColumnInfo var isUploadedToServer: Boolean = false
) : Serializable

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
    HEALTH_FACILITY_NAME("healthFacilityName"),
    ACTION_TAKEN("actionTaken"),
    CANCEL_REASON("cancelReason"),
    NOT_ATTEND_REASON("notAttendReason"),
    IS_ASSESSED("isAssessed"),
    IS_CANCELLED("isCancelled"),
    NOT_ATTENDED("notAttended"),
    LAST_EDITED("lastEdited"),
    LAST_SERVER_UPDATE("lastServerUpdate")
}
