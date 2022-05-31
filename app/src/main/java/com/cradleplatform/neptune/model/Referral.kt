package com.cradleplatform.neptune.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeLongField
import com.cradleplatform.neptune.ext.jackson.writeOptIntField
import com.cradleplatform.neptune.ext.jackson.writeOptLongField
import com.cradleplatform.neptune.ext.jackson.writeOptStringField
import com.cradleplatform.neptune.ext.jackson.writeStringField
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.Serializable
import java.util.UUID

/**
 * Holds information about a referral.
 *
 * @property comment An optional comment made by the user about this referral
 * @property referralHealthFacilityName The name of the health facility this referral
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
        Index(value = ["referralHealthFacilityName"])
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
            childColumns = arrayOf("referralHealthFacilityName"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(using = Referral.Serializer::class)
@JsonDeserialize(using = Referral.Deserializer::class)
data class Referral(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    var id: String = UUID.randomUUID().toString(),

    @ColumnInfo @JsonProperty("comment")
    var comment: String?,

    @ColumnInfo @JsonProperty("referralHealthFacilityName")
    var referralHealthFacilityName: String,

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
) : Serializable {
    class Serializer : StdSerializer<Referral>(Referral::class.java) {
        override fun serialize(
            referral: Referral,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            referral.run {
                gen.writeStartObject()

                gen.writeStringField(ReferralField.ID, id)
                gen.writeOptStringField(ReferralField.COMMENT, comment)
                gen.writeStringField(ReferralField.HEALTH_FACILITY_NAME, referralHealthFacilityName)
                gen.writeLongField(ReferralField.DATE_REFERRED, dateReferred)
                gen.writeOptIntField(ReferralField.USER_ID, userId)
                gen.writeStringField(ReferralField.PATIENT_ID, patientId)
                gen.writeOptStringField(ReferralField.ACTION_TAKEN, actionTaken)
                gen.writeOptStringField(ReferralField.CANCEL_REASON, cancelReason)
                gen.writeOptStringField(ReferralField.NOT_ATTEND_REASON, notAttendReason)
                gen.writeBooleanField(ReferralField.IS_ASSESSED, isAssessed)
                gen.writeBooleanField(ReferralField.IS_CANCELLED, isCancelled)
                gen.writeBooleanField(ReferralField.NOT_ATTENDED, notAttended)
                gen.writeLongField(ReferralField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(ReferralField.LAST_SERVER_UPDATE, lastServerUpdate)

                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<Referral>(Referral::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Referral =
            p.codec.readTree<JsonNode>(p)!!.run {
                val id = get(ReferralField.ID)!!.textValue()
                val comment = get(ReferralField.COMMENT)?.textValue()
                val referralHealthFacilityName = get(ReferralField.HEALTH_FACILITY_NAME)!!.textValue()
                val dateReferred = get(ReferralField.DATE_REFERRED)!!.longValue()
                val userId = get(ReferralField.USER_ID)?.intValue()
                val patientId = get(ReferralField.PATIENT_ID)!!.textValue()
                val actionTaken = get(ReferralField.ACTION_TAKEN)?.textValue()
                val cancelReason = get(ReferralField.CANCEL_REASON)?.textValue()
                val notAttendReason = get(ReferralField.NOT_ATTEND_REASON)?.textValue()
                val isAssessed = get(ReferralField.IS_ASSESSED)!!.booleanValue()
                val isCancelled = get(ReferralField.IS_CANCELLED)!!.booleanValue()
                val notAttended = get(ReferralField.NOT_ATTENDED)!!.booleanValue()
                val lastEdited = get(ReferralField.LAST_EDITED)!!.longValue()
                val lastServerUpdate = get(ReferralField.LAST_SERVER_UPDATE)?.longValue()

                return@run Referral(
                    id = id,
                    comment = comment,
                    referralHealthFacilityName = referralHealthFacilityName,
                    dateReferred = dateReferred,
                    userId = userId,
                    patientId = patientId,
                    actionTaken = actionTaken,
                    cancelReason = cancelReason,
                    notAttendReason = notAttendReason,
                    isAssessed = isAssessed,
                    isCancelled = isCancelled,
                    notAttended = notAttended,
                    lastEdited = lastEdited,
                    lastServerUpdate = lastServerUpdate
                )
            }
    }

    object AscendingDataComparator : Comparator<Referral> {
        override fun compare(o1: Referral?, o2: Referral?): Int {
            val hasO1 = o1?.dateReferred != null
            val hasO2 = o2?.dateReferred != null
            return when {
                hasO1 && hasO2 -> o1!!.dateReferred.compareTo(o2!!.dateReferred)
                hasO1 && !hasO2 -> -1
                !hasO1 && hasO2 -> 1
                else -> 0
            }
        }
    }

    object DescendingDateComparator : Comparator<Referral> {
        override fun compare(o1: Referral?, o2: Referral?): Int =
            -AscendingDataComparator.compare(o1, o2)
    }
}

/**
 * The information that is expected by the SMS relay app.
 * TODO: Ensure that removing "referralId" doesn't break the SMS relay app. (refer to issue #31)
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
    ACTION_TAKEN("actionTaken"),
    CANCEL_REASON("cancelReason"),
    NOT_ATTEND_REASON("notAttendReason"),
    IS_ASSESSED("isAssessed"),
    IS_CANCELLED("isCancelled"),
    NOT_ATTENDED("notAttended"),
    LAST_EDITED("lastEdited"),
    LAST_SERVER_UPDATE("lastServerUpdate")
}
