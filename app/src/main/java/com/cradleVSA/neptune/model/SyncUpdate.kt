package com.cradleVSA.neptune.model

import com.cradleVSA.neptune.ext.Field
import com.cradleVSA.neptune.ext.jackson.getObjectArray
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 * API response model for the `/sync/updates` endpoint. Contains lists of
 * new/edited patients, readings, and assessments (aka. followups).
 *
 * @property newPatientsIds List of ids of new patients that have been created
 *  since the last sync
 * @property editedPatientsIds List of ids of patients which have been edited
 *  since the last sync
 * @property newReadingsIds List of ids of new readings since the last sync
 * @property followupIds List of ids of new followups since the last sync
 */
@JsonDeserialize(using = SyncUpdate.Deserializer::class)
data class SyncUpdate(
    val newPatientsIds: Set<String>,
    val editedPatientsIds: Set<String>,
    val newReadingsIds: Set<String>,
    val followupIds: Set<String>
) {
    constructor(
        newPatientsIds: List<String>,
        editedPatientsIds: List<String>,
        newReadingsIds: List<String>,
        followupIds: List<String>
    ) : this(
        HashSet(newPatientsIds),
        HashSet(editedPatientsIds),
        HashSet(newReadingsIds),
        HashSet(followupIds)
    )

    class Deserializer : StdDeserializer<SyncUpdate>(SyncUpdate::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): SyncUpdate {
            p.codec.readTree<JsonNode>(p)!!.run {
                return SyncUpdate(
                    getObjectArray(SyncUpdateField.NEW_PATIENTS, p.codec),
                    getObjectArray(SyncUpdateField.EDITED_PATIENTS, p.codec),
                    getObjectArray(SyncUpdateField.READINGS, p.codec),
                    getObjectArray(SyncUpdateField.FOLLOWUPS, p.codec),
                )
            }
        }
    }
}

private enum class SyncUpdateField(override val text: String) : Field {
    NEW_PATIENTS("newPatients"),
    EDITED_PATIENTS("editedPatients"),
    READINGS("readings"),
    FOLLOWUPS("followups"),
}
