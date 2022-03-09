package com.cradleplatform.neptune.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeIntField
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
 * @property lastEdited Last time assessment was edited on android
 * @property lastServerUpdate Last time the assessment has gotten updated from the server.
 */
@Entity(
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["patientId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("patientId"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(using = Assessment.Serializer::class)
@JsonDeserialize(using = Assessment.Deserializer::class)
data class Assessment(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    var id: Int?,

    @ColumnInfo @JsonProperty("dateAssessed")
    var dateAssessed: Long,

    @ColumnInfo @JsonProperty("healthCareWorkerId")
    var healthCareWorkerId: Int,

    @ColumnInfo @JsonProperty("patientId")
    var patientId: String,

    @ColumnInfo @JsonProperty("diagnosis")
    var diagnosis: String?,

    @ColumnInfo @JsonProperty("treatment")
    var treatment: String?,

    @ColumnInfo @JsonProperty("medicationPrescribed")
    var medicationPrescribed: String?,

    @ColumnInfo @JsonProperty("specialInvestigations")
    var specialInvestigations: String?,

    @ColumnInfo @JsonProperty("followupNeeded")
    var followupNeeded: Boolean,

    @ColumnInfo @JsonProperty("followupInstructions")
    var followupInstructions: String?,

    @ColumnInfo @JsonProperty("lastEdited")
    var lastEdited: Long? = null,

    @ColumnInfo @JsonProperty("lastServerUpdate")
    var lastServerUpdate: Long? = null
) : Serializable {
    class Serializer : StdSerializer<Assessment>(Assessment::class.java) {
        override fun serialize(
            assessment: Assessment,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            assessment.run {
                gen.writeStartObject()

                gen.writeOptIntField(AssessmentField.ID, id)
                gen.writeLongField(AssessmentField.DATE_ASSESSED, dateAssessed)
                gen.writeIntField(AssessmentField.HEALTH_CARE_WORKER_ID, healthCareWorkerId)
                gen.writeStringField(AssessmentField.PATIENT_ID, patientId)
                gen.writeOptStringField(AssessmentField.DIAGNOSIS, diagnosis)
                gen.writeOptStringField(AssessmentField.TREATMENT, treatment)
                gen.writeOptStringField(AssessmentField.MEDICATION_PRESCRIBED, medicationPrescribed)
                gen.writeOptStringField(AssessmentField.SPECIAL_INVESTIGATIONS, specialInvestigations)
                gen.writeBooleanField(AssessmentField.FOLLOW_UP_NEEDED, followupNeeded)
                gen.writeOptStringField(AssessmentField.FOLLOW_UP_INSTRUCTIONS, followupInstructions)
                gen.writeOptLongField(AssessmentField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(AssessmentField.LAST_SERVER_UPDATE, lastServerUpdate)

                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<Assessment>(Assessment::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Assessment =
            p.codec.readTree<JsonNode>(p)!!.run {
                val id = get(AssessmentField.ID)?.intValue()
                val dateAssessed = get(AssessmentField.DATE_ASSESSED)!!.longValue()
                val healthCareWorkerId = get(AssessmentField.HEALTH_CARE_WORKER_ID)!!.intValue()
                val patientId = get(AssessmentField.PATIENT_ID)!!.textValue()
                val diagnosis = get(AssessmentField.DIAGNOSIS)?.textValue()
                val treatment = get(AssessmentField.TREATMENT)?.textValue()
                val medicationPrescribed = get(AssessmentField.MEDICATION_PRESCRIBED)?.textValue()
                val specialInvestigations = get(AssessmentField.SPECIAL_INVESTIGATIONS)?.textValue()
                val followupNeeded = get(AssessmentField.FOLLOW_UP_NEEDED)!!.booleanValue()
                val followupInstructions = get(AssessmentField.FOLLOW_UP_INSTRUCTIONS)?.textValue()
                val lastEdited = get(AssessmentField.LAST_EDITED)?.longValue()
                val lastServerUpdate = get(AssessmentField.LAST_SERVER_UPDATE)?.longValue()

                return@run Assessment(
                    id = id,
                    dateAssessed = dateAssessed,
                    healthCareWorkerId = healthCareWorkerId,
                    patientId = patientId,
                    diagnosis = diagnosis,
                    treatment = treatment,
                    medicationPrescribed = medicationPrescribed,
                    specialInvestigations= specialInvestigations,
                    followupNeeded = followupNeeded,
                    followupInstructions= followupInstructions,
                    lastEdited= lastEdited,
                    lastServerUpdate = lastServerUpdate
                )
            }
    }
}

private enum class AssessmentField(override val text: String) : Field {
    ID("id"),
    DATE_ASSESSED("dateAssessed"),
    HEALTH_CARE_WORKER_ID("healthcareWorkerId"),
    PATIENT_ID("patientId"),
    DIAGNOSIS("diagnosis"),
    TREATMENT("treatment"),
    MEDICATION_PRESCRIBED("medicationPrescribed"),
    SPECIAL_INVESTIGATIONS("specialInvestigations"),
    FOLLOW_UP_NEEDED("followupNeeded"),
    FOLLOW_UP_INSTRUCTIONS("followupInstructions"),
    LAST_EDITED("lastEdited"),
    LAST_SERVER_UPDATE("lastServerUpdate")
}
