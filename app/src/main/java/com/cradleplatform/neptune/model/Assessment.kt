package com.cradleplatform.neptune.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cradleplatform.neptune.ext.Field
import com.cradleplatform.neptune.ext.jackson.get
import com.cradleplatform.neptune.ext.jackson.writeIntField
import com.cradleplatform.neptune.ext.jackson.writeLongField
import com.cradleplatform.neptune.ext.jackson.writeOptBooleanField
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
 * @property id A unique id for this assessment populated by the server.
 * @property dateAssessed The time this assessment was made as a unix timestamp.
 * @property healthcareWorkerId The id of the user who made this assessment.
 * @property patientId Id of the patient this assessment belongs to.
 * @property diagnosis An optional medical diagnosis.
 * @property treatment An optional treatment description.
 * @property medicationPrescribed An optional description of the medication
 *  prescribed to the patient.
 * @property followUpNeeded True if a follow up is required by the VHT.
 * @property followUpInstructions Instructions for the follow up if required.
 * @property lastEdited Last time assessment was edited on android.
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
@JsonSerialize(using = Assessment.Serializer::class)
@JsonDeserialize(using = Assessment.Deserializer::class)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Assessment(
    @PrimaryKey @ColumnInfo @JsonProperty("id")
    var id: String,

    @ColumnInfo @JsonProperty("dateAssessed")
    var dateAssessed: Long,

    @ColumnInfo @JsonProperty("healthcareWorkerId")
    var healthcareWorkerId: Int,

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

    @ColumnInfo @JsonProperty("followUpNeeded")
    var followUpNeeded: Boolean?,

    @ColumnInfo @JsonProperty("followUpInstructions")
    var followUpInstructions: String?,

    @ColumnInfo @JsonProperty("lastEdited")
    var lastEdited: Long? = null,

    @ColumnInfo @JsonProperty("lastServerUpdate")
    var lastServerUpdate: Long? = null,

    @ColumnInfo var isUploadedToServer: Boolean = false
) : Serializable {
    class Serializer : StdSerializer<Assessment>(Assessment::class.java) {
        override fun serialize(
            assessment: Assessment,
            gen: JsonGenerator,
            provider: SerializerProvider
        ) {
            assessment.run {
                gen.writeStartObject()

                gen.writeStringField(AssessmentField.ID, id)
                gen.writeLongField(AssessmentField.DATE_ASSESSED, dateAssessed)
                gen.writeIntField(AssessmentField.HEALTHCARE_WORKER_ID, healthcareWorkerId)
                gen.writeStringField(AssessmentField.PATIENT_ID, patientId)
                gen.writeOptStringField(AssessmentField.DIAGNOSIS, diagnosis)
                gen.writeOptStringField(AssessmentField.TREATMENT, treatment)
                gen.writeOptStringField(AssessmentField.MEDICATION_PRESCRIBED, medicationPrescribed)
                gen.writeOptStringField(AssessmentField.SPECIAL_INVESTIGATIONS, specialInvestigations)
                gen.writeOptBooleanField(AssessmentField.FOLLOW_UP_NEEDED, followUpNeeded)
                gen.writeOptStringField(AssessmentField.FOLLOW_UP_INSTRUCTIONS, followUpInstructions)
                gen.writeOptLongField(AssessmentField.LAST_EDITED, lastEdited)
                gen.writeOptLongField(AssessmentField.LAST_SERVER_UPDATE, lastServerUpdate)

                gen.writeEndObject()
            }
        }
    }

    class Deserializer : StdDeserializer<Assessment>(Assessment::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Assessment =
            p.codec.readTree<JsonNode>(p)!!.run {
                val id = get(AssessmentField.ID)!!.textValue()
                val dateAssessed = get(AssessmentField.DATE_ASSESSED)!!.longValue()
                val healthcareWorkerId = get(AssessmentField.HEALTHCARE_WORKER_ID)!!.intValue()
                val patientId = get(AssessmentField.PATIENT_ID)!!.textValue()
                val diagnosis = get(AssessmentField.DIAGNOSIS)?.textValue()
                val treatment = get(AssessmentField.TREATMENT)?.textValue()
                val medicationPrescribed = get(AssessmentField.MEDICATION_PRESCRIBED)?.textValue()
                val specialInvestigations = get(AssessmentField.SPECIAL_INVESTIGATIONS)?.textValue()
                val followUpNeeded = get(AssessmentField.FOLLOW_UP_NEEDED)?.booleanValue()
                val followUpInstructions = get(AssessmentField.FOLLOW_UP_INSTRUCTIONS)?.textValue()
                val lastEdited = get(AssessmentField.LAST_EDITED)?.longValue()
                val lastServerUpdate = get(AssessmentField.LAST_SERVER_UPDATE)?.longValue()

                return@run Assessment(
                    id = id,
                    dateAssessed = dateAssessed,
                    healthcareWorkerId = healthcareWorkerId,
                    patientId = patientId,
                    diagnosis = diagnosis,
                    treatment = treatment,
                    medicationPrescribed = medicationPrescribed,
                    specialInvestigations = specialInvestigations,
                    followUpNeeded = followUpNeeded,
                    followUpInstructions = followUpInstructions,
                    lastEdited = lastEdited,
                    lastServerUpdate = lastServerUpdate
                )
            }
    }

    object AscendingDataComparator : Comparator<Assessment> {
        override fun compare(o1: Assessment?, o2: Assessment?): Int {
            val hasO1 = o1?.dateAssessed != null
            val hasO2 = o2?.dateAssessed != null
            return when {
                hasO1 && hasO2 -> o1!!.dateAssessed.compareTo(o2!!.dateAssessed)
                hasO1 && !hasO2 -> -1
                !hasO1 && hasO2 -> 1
                else -> 0
            }
        }
    }

    object DescendingDateComparator : Comparator<Assessment> {
        override fun compare(o1: Assessment?, o2: Assessment?): Int =
            -AscendingDataComparator.compare(o1, o2)
    }
}

private enum class AssessmentField(override val text: String) : Field {
    ID("id"),
    DATE_ASSESSED("dateAssessed"),
    HEALTHCARE_WORKER_ID("healthcareWorkerId"),
    PATIENT_ID("patientId"),
    DIAGNOSIS("diagnosis"),
    TREATMENT("treatment"),
    MEDICATION_PRESCRIBED("medicationPrescribed"),
    SPECIAL_INVESTIGATIONS("specialInvestigations"),
    FOLLOW_UP_NEEDED("followUpNeeded"),
    FOLLOW_UP_INSTRUCTIONS("followUpInstructions"),
    LAST_EDITED("lastEdited"),
    LAST_SERVER_UPDATE("lastServerUpdate")
}
