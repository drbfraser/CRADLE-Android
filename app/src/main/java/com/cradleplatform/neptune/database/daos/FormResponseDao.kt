package com.cradleplatform.neptune.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cradleplatform.neptune.model.FormResponse

/**
 * Data Access Object (DAO) for [FormResponse] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface FormResponseDao {

    /**
     * Updates or inserts a new [FormResponse]. Checking is done through form response's id.
     *
     * @param formResponse [FormResponse] The form response that should be updated or inserted
     */
    @Transaction
    suspend fun updateOrInsertIfNotExists(formResponse: FormResponse) {
        val existingFormResponse = getFormResponseById(formResponse.formResponseId)
        if (existingFormResponse != null) {
            delete(existingFormResponse)
            insert(formResponse)
        } else {
            insert(formResponse)
        }
    }

    /**
     * Inserts a new form response into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param formResponse The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(formResponse: FormResponse)

    /**
     * Inserts each formResponse in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param formResponseEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(formResponseEntities: List<FormResponse>)

    /**
     * Updates an existing formResponse in the database.
     *
     * @param formResponse An entity containing updated data.
     * @return the number of rows updated (i.e., 0 means the given formResponse wasn't in the database,
     * and 1 means the [formResponse] was updated)
     */
    @Update
    suspend fun update(formResponse: FormResponse): Int

    /**
     * Removes an entity from the database.
     *
     * @param formResponse The entity to remove.
     */
    @Delete
    suspend fun delete(formResponse: FormResponse)

    /**
     * Deletes a [FormResponse] with the [formResponseId].
     *
     * @param formResponseId [Long] The id of the form response that should be deleted
     * @return The number of rows affected  in the database(i.e., 1 if a [FormResponse] with the given
     * [formResponseId] was found and deleted, 0 if not found).
     */
    @Query("DELETE FROM FormResponse WHERE formResponseId = :formResponseId")
    suspend fun deleteById(formResponseId: Long): Int

    /**
     * Returns the first formResponse whose formResponse ID is equal to [id].
     *
     * @param id The formResponse id to search for.
     */
    @Query("SELECT * FROM FormResponse WHERE formResponseId = :id")
    suspend fun getFormResponseById(id: Long): FormResponse?

    /**
     * Returns all of the formResponses associated with a specified patient.
     *
     * @param id The id of the patient to find formResponses for.
     */
    @Query("SELECT * FROM FormResponse WHERE patientId = :id")
    suspend fun getAllFormResponseByPatientId(id: String): MutableList<FormResponse>?

    /**
     * Returns a live list of formResponses
     */
    @Query("SELECT * FROM FormResponse")
    fun getAllFormResponsesLiveData(): LiveData<List<FormResponse>>

    /**
     * Returns a list of formResponses
     */
    @Query("SELECT * FROM FormResponse")
    fun getAllFormResponses(): List<FormResponse>

    @Query("SELECT * FROM FormResponse WHERE patientId = :patientId AND saveResponseToSendLater = false")
    suspend fun getSubmittedForms(patientId: String): MutableList<FormResponse>

    @Query("SELECT * FROM FormResponse WHERE patientId = :patientId AND saveResponseToSendLater = true")
    suspend fun getDraftForms(patientId: String): MutableList<FormResponse>

    @Query("SELECT * FROM FormResponse WHERE saveResponseToSendLater = true")
    suspend fun getAllDraftForms(): MutableList<FormResponse>
}
