package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cradleplatform.neptune.model.FormTemplateV2Entity

@Dao
abstract class FormTemplateV2Dao {
    @Query("SELECT * FROM FormTemplateV2Entity WHERE id = :id")
    abstract suspend fun getTemplate(id: String): FormTemplateV2Entity?

    @Query("SELECT * FROM FormTemplateV2Entity WHERE availableForNewForms = 1")
    abstract suspend fun getAvailableTemplates(): List<FormTemplateV2Entity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTemplates(templates: List<FormTemplateV2Entity>)

    @Query("UPDATE FormTemplateV2Entity SET availableForNewForms = 0")
    protected abstract suspend fun markTemplatesUnavailable()

    @Transaction
    open suspend fun replaceAvailableTemplates(templates: List<FormTemplateV2Entity>) {
        // Older templates remain available to read historical submissions.
        markTemplatesUnavailable()
        insertTemplates(templates.map { it.copy(availableForNewForms = true) })
    }

    @Transaction
    open suspend fun saveTemplate(template: FormTemplateV2Entity) {
        val existing = getTemplate(template.id)
        insertTemplates(listOf(template.copy(availableForNewForms = existing?.availableForNewForms ?: false)))
    }
}
