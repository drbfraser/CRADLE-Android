package com.cradle.neptune.model

import com.cradle.neptune.ext.Field
import com.cradle.neptune.ext.arrayField
import com.cradle.neptune.ext.toList
import org.json.JSONArray
import org.json.JSONObject

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
data class SyncUpdate(
    val newPatientsIds: Set<String>,
    val editedPatientsIds: Set<String>,
    val newReadingsIds: Set<String>,
    val followupIds: Set<String>
) {
    companion object : Unmarshal<SyncUpdate, JSONObject> {
        override fun unmarshal(data: JSONObject): SyncUpdate =
            SyncUpdate(
                HashSet(data.arrayField(SyncUpdateField.NEW_PATIENTS).toList(JSONArray::getString)),
                HashSet(data.arrayField(SyncUpdateField.EDITED_PATIENTS).toList(JSONArray::getString)),
                HashSet(data.arrayField(SyncUpdateField.READINGS).toList(JSONArray::getString)),
                HashSet(data.arrayField(SyncUpdateField.FOLLOWUPS).toList(JSONArray::getString))
            )
    }
}

private enum class SyncUpdateField(override val text: String) : Field {
    NEW_PATIENTS("newPatients"),
    EDITED_PATIENTS("editedPatients"),
    READINGS("readings"),
    FOLLOWUPS("followups"),
}
