package com.cradleplatform.neptune.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cradleplatform.neptune.model.Assessment

/**
 * Data Access Object (DAO) for [Assessment] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface AssessmentDao {
    @Transaction
    suspend fun updateOrInsertIfNotExists(assessment: Assessment) {
        if (update(assessment) <= 0) {
            insert(assessment)
        }
    }

    /**
     * Inserts a new assessment into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param assessment The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assessment: Assessment)

    /**
     * Inserts each assessment in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param assessmentEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assessmentEntities: List<Assessment>)

    /**
     * Updates an existing referral in the database.
     *
     * @param assessment An entity containing updated data.
     * @return the number of rows updated (i.e., 0 means the given assessment wasn't in the database,
     * and 1 means the [assessment] was updated)
     */
    @Update
    suspend fun update(assessment: Assessment): Int

    /**
     * Removes an entity from the database.
     *
     * @param assessment The entity to remove.
     */
    @Delete
    suspend fun delete(assessment: Assessment?)

    /**
     * Returns the first assessment whose assessment ID is equal to [id].
     *
     * @param id The assessment id to search for.
     */
    @Query("SELECT * FROM Assessment WHERE id = :id")
    suspend fun getAssessmentById(id: Int): Assessment?

    /**
     * Returns all of the assessments associated with a specified patient.
     *
     * @param id The id of the patient to find assessments for.
     */
    @Query("SELECT * FROM Assessment WHERE patientId = :id")
    suspend fun getAllAssessmentByPatientId(id: String): List<Assessment>?

    /**
     * Query the database for all the assessments that have been created or edited offline
     */
    @Query("SELECT * FROM Assessment WHERE isUploadedToServer = 0")
    suspend fun assessmentsToUpload(): List<Assessment>

    /**
     * Query the database for the number of assessments that have been created or edited offline
     */
    @Query("SELECT COUNT(id) FROM Assessment WHERE isUploadedToServer = 0")
    suspend fun countAssessmentsToUpload(): Int

    /**
     * Returns a live list of assessments
     */
    // TODO: need live data?
    // if need, need for all assessment or based on patient id?
    @Query("SELECT * FROM Assessment")
    fun getAllAssessmentsLiveData(): LiveData<List<Assessment>>
}
