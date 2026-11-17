package com.cradleplatform.neptune.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.cradleplatform.neptune.database.FormSubmissionV2TypeConverters

/** A downloaded submission with its answers; full templates remain in FormTemplateV2Entity. */
@Entity(
    primaryKeys = ["id"],
    indices = [Index(value = ["patientId"])],
    foreignKeys = [ForeignKey(
        entity = Patient::class,
        parentColumns = ["id"],
        childColumns = ["patientId"],
        onUpdate = ForeignKey.CASCADE,
        onDelete = ForeignKey.CASCADE
    )]
)
@TypeConverters(FormSubmissionV2TypeConverters::class)
data class FormSubmissionV2Entity(
    @Embedded val submission: FormSubmissionV2,
    val answers: List<AnswerWithQuestionV2>
)
