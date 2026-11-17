package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FormSubmissionV2Dao {
    @Query("SELECT * FROM FormSubmissionV2Entity WHERE id = :id AND patientId = :patientId")
    abstract suspend fun getSubmission(id: String, patientId: String): FormSubmissionV2Entity?

    @Query("SELECT * FROM FormSubmissionV2Entity WHERE patientId = :patientId ORDER BY dateSubmitted DESC, id")
    abstract fun observeSubmissionsByPatientId(patientId: String): Flow<List<FormSubmissionV2Entity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(submission: FormSubmissionV2Entity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSubmissions(submissions: List<FormSubmissionV2Entity>)

    @Query("DELETE FROM FormSubmissionV2Entity WHERE patientId = :patientId")
    protected abstract suspend fun deleteSubmissionsByPatientId(patientId: String)

    /** Call only after the entire patient's submission list and answers have downloaded successfully. */
    @Transaction
    open suspend fun replacePatientSubmissions(patientId: String, submissions: List<FormSubmissionV2Entity>) {
        require(submissions.all { it.submission.patientId == patientId }) {
            "Submissions must belong to the requested patient"
        }
        deleteSubmissionsByPatientId(patientId)
        insertSubmissions(submissions)
    }
}
