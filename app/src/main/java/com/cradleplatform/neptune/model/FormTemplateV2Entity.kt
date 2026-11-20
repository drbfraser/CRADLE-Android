package com.cradleplatform.neptune.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.cradleplatform.neptune.database.FormV2DraftTypeConverters

/** Full templates are also retained for submissions using an older template version. */
@Entity
@TypeConverters(FormV2DraftTypeConverters::class)
data class FormTemplateV2Entity(
    @PrimaryKey val id: String,
    val template: FormTemplateV2,
    val availableForNewForms: Boolean = false
) {
    init {
        require(id == template.id) { "Cached template ID must match its snapshot." }
    }
}
