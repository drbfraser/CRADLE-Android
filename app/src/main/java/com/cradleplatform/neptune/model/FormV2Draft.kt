package com.cradleplatform.neptune.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.TypeConverters
import com.cradleplatform.neptune.database.FormV2DraftTypeConverters

/**
 * A locally persisted, unfinished V2 form.
 *
 * The composite primary key enforces the product rule that a patient can have at most one draft
 * for a template. The full template snapshot keeps the questions used by the saved answers
 * available even if the server template changes later. Timestamps are epoch milliseconds.
 */
@Entity(
    primaryKeys = ["patientId", "formTemplateId"],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(FormV2DraftTypeConverters::class)
data class FormV2Draft(
    val patientId: String,
    val formTemplateId: String,
    val formTemplateVersion: Int,
    val formTemplate: FormTemplateV2,
    val answers: List<FormAnswerV2>,
    val language: String = "English",
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        // Keep the row identity and its serialized template snapshot from silently diverging.
        require(patientId.isNotBlank()) { "A draft requires a patient ID" }
        require(formTemplateId.isNotBlank() && formTemplateId == formTemplate.id) {
            "Draft template ID must match its snapshot"
        }
        require(formTemplateVersion == formTemplate.version) {
            "Draft template version must match its snapshot"
        }
        require(updatedAt >= createdAt) { "A draft cannot be updated before it was created" }
    }
}
