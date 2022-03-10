package com.cradleplatform.neptune.model

import android.content.SharedPreferences
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.getOptObject
import com.cradleplatform.neptune.ext.jackson.getOptObjectArray
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeIntField
import com.cradleplatform.neptune.ext.jackson.writeLongField
import com.cradleplatform.neptune.ext.jackson.writeObjectField
import com.cradleplatform.neptune.ext.jackson.writeOptIntField
import com.cradleplatform.neptune.ext.jackson.writeOptLongField
import com.cradleplatform.neptune.ext.jackson.writeOptObjectField
import com.cradleplatform.neptune.ext.jackson.writeOptStringField
import com.cradleplatform.neptune.ext.jackson.writeStringField
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.utilities.nullIfEmpty
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
@JsonSerialize(using = Referral.Serializer::class)
@JsonDeserialize(using = Referral.Deserializer::class)
data class Referral(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    var id: Int?,

    @ColumnInfo @JsonProperty("comment")// TODO here should match web
    var comment: String?,

    @ColumnInfo @JsonProperty("referralHealthFacilityName")// TODO consider delete jsonproperty
    var healthFacilityName: String,

    @ColumnInfo @JsonProperty("dateReferred")
    var dateReferred: Long,

    @ColumnInfo @JsonProperty("userId")
    var userId: Int?,

    @ColumnInfo @JsonProperty("patientId")
    var patientId: String,

    @ColumnInfo @JsonProperty("readingId")
    var readingId: String,

    @ColumnInfo @JsonProperty("isAssessed")
    var isAssessed: Boolean,

    @ColumnInfo @JsonProperty("lastEdited")
    var lastEdited: Long? = null,

    @ColumnInfo @JsonProperty("lastServerUpdate")
    var lastServerUpdate: Long? = null
) : Serializable {
    class Serializer : StdSerializer<Referral>(Referral::class.java) {
        override fun serialize(
            referral: Referral,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            referral.run {
                gen.writeStartObject()

                gen.writeOptIntField(ReferralField.ID, id)
                gen.writeOptStringField(ReferralField.COMMENT, comment)
                gen.writeStringField(ReferralField.HEALTH_FACILITY_NAME, healthFacilityName)
                gen.writeLongField(ReferralField.DATE_REFERRED, dateReferred)
                gen.writeOptIntField(ReferralField.USER_ID, userId)
                gen.writeStringField(ReferralField.PATIENT_ID, patientId)
                // gen.writeStringField(ReferralField.READING_ID, readingId)
                gen.writeBooleanField(ReferralField.IS_ASSESSED, isAssessed)
                gen.writeOptLongField(ReferralField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(ReferralField.LAST_SERVER_UPDATE, lastServerUpdate)

                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<Referral>(Referral::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Referral =
            p.codec.readTree<JsonNode>(p)!!.run {
                val id = get(ReferralField.ID)?.intValue()
                val comment = get(ReferralField.COMMENT)?.textValue()
                val healthFacilityName = get(ReferralField.HEALTH_FACILITY_NAME)!!.textValue()
                val dateReferred = get(ReferralField.DATE_REFERRED)!!.longValue()
                val userId = get(ReferralField.USER_ID)?.intValue()
                val patientId = get(ReferralField.PATIENT_ID)!!.textValue()
                // val readingId = get(ReferralField.READING_ID)!!.textValue()
                val isAssessed = get(ReferralField.IS_ASSESSED)!!.booleanValue()
                val lastEdited = get(ReferralField.LAST_EDITED)?.longValue()
                val lastServerUpdate = get(ReferralField.LAST_SERVER_UPDATE)?.longValue()

                return@run Referral(
                    id = id,
                    comment = comment,
                    healthFacilityName = healthFacilityName,
                    dateReferred = dateReferred,
                    userId = userId,
                    patientId = patientId,
                    readingId = "1",
                    isAssessed = isAssessed,
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

    // constructor(
    //     comment: String?,
    //     healthFacilityName: String,
    //     dateReferred: Long,
    //     patientId: String,
    //     readingId: String,
    //     sharedPreferences: SharedPreferences
    // ) : this(
    //     comment = comment,
    //     healthFacilityName = healthFacilityName,
    //     dateReferred = dateReferred,
    //     id = null,
    //     userId = sharedPreferences.getIntOrNull(LoginManager.USER_ID_KEY),
    //     patientId = patientId,
    //     readingId = readingId,
    //     isAssessed = false
    // )
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
    IS_ASSESSED("isAssessed"),
    LAST_EDITED("lastEdited"),
    LAST_SERVER_UPDATE("lastServerUpdate")
}

// private fun find(): Int {
//     var sharedPreferences: SharedPreferences
//     return sharedPreferences.getIntOrNull(LoginManager.USER_ID_KEY)
// }