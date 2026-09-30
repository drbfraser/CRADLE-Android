package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cradleplatform.neptune.model.FormV2Draft

@Dao
abstract class FormV2DraftDao {
    /**
     * Inserts a new draft or updates the existing draft for the same patient and template.
     *
     * This uses an explicit update instead of REPLACE so the original creation timestamp remains
     * stable while the caller-provided update timestamp and draft contents are replaced.
     */
    @Transaction
    open suspend fun upsert(draft: FormV2Draft) {
        val existing = getDraft(draft.patientId, draft.formTemplateId)
        if (existing == null) {
            insertDraft(draft)
        } else {
            updateDraft(draft.copy(createdAt = existing.createdAt))
        }
    }

    @Insert
    protected abstract suspend fun insertDraft(draft: FormV2Draft)

    @Update
    protected abstract suspend fun updateDraft(draft: FormV2Draft)

    @Query("SELECT * FROM FormV2Draft WHERE patientId = :patientId AND formTemplateId = :formTemplateId")
    abstract suspend fun getDraft(patientId: String, formTemplateId: String): FormV2Draft?

    @Query("DELETE FROM FormV2Draft WHERE patientId = :patientId AND formTemplateId = :formTemplateId")
    abstract suspend fun deleteDraft(patientId: String, formTemplateId: String): Int
}
