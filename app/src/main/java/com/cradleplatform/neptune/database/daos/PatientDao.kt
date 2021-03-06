package com.cradleplatform.neptune.database.daos

import androidx.annotation.VisibleForTesting
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings

@Dao
interface PatientDao {
    @Transaction
    suspend fun updateOrInsertIfNotExists(patient: Patient) {
        if (update(patient) <= 0) {
            insert(patient)
        }
    }

    /**
     * Inserts [patient] into the [Patient] table.
     *
     * DO NOT use this to update a [Patient]; use [update] or [updateOrInsertIfNotExists] for that.
     * If this is used to update a [Patient] in the database, any Readings with foreign keys
     * pointing to the "updated" Patient will cascade and delete themselves, because Room's
     * OnConflictStrategy.REPLACE somehow involves deleting the entity and reading it.
     *
     * @return the new SQLite rowId for the inserted [patient], or -1 if [patient] was not inserted
     * into the database. -1 might occur if the [patient] already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @VisibleForTesting
    suspend fun insert(patient: Patient): Long

    /**
     * @return the number of rows updated (i.e., 0 means the given patient wasn't in the database,
     * and 1 means the [patient] was updated)
     */
    @Update
    suspend fun update(patient: Patient): Int

    /**
     * Insert a list of [Patient]s into the [Patient] table. This is meant for
     * NEW patients. It will not update patients that already exist in the database.
     * This is because SQLite's REPLACE doesn't work well with foreign keys. Readings
     * with foreign keys pointing to the replaced Patients will cascade and delete
     * themselves, because Room's OnConflictStrategy.REPLACE somehow involves deleting
     * the entity and replacing it.
     *
     * If there are patients in the list that are not new, those patients will have -1 as the id for
     * them in the returned Array of SQLite rowIds.
     *
     * @return An Array of the SQLite rowIds for the inserted [Patient]s, where the positions in the
     * Array correspond to the position in the given [patients] list. -1 means the Patient was not
     * inserted
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(patients: List<Patient>): LongArray

    /**
     * Deletes a patient from the patient table.
     *
     * @return The number of rows affected in the database (i.e., 0 means no [Patient]s were deleted
     * and 1 means the given [patient] was deleted).
     */
    @Delete
    suspend fun delete(patient: Patient): Int

    /**
     * Deletes a [Patient] with the [patientId].
     * @return The number of rows affected  in the database(i.e., 1 if a [Patient] with the given
     * [patientId] was found and deleted, 0 if not found).
     */
    @Query("DELETE FROM Patient WHERE id = :patientId")
    suspend fun deleteById(patientId: String): Int

    /**
     * Gets all patients in a local database in [LocalSearchPatient] form for displaying in a list.
     *
     * The result will be sorted in descending order by latest reading date and then by name (more
     * applicable for patients without a reading). Patients that don't have a reading will be
     * placed below all other patients that have readings.
     */
    @Query(
        """
SELECT * FROM LocalSearchPatient
ORDER BY latestReadingDate DESC, name COLLATE NOCASE ASC
    """
    )
    fun allLocalSearchPatientsByDate(): PagingSource<Int, LocalSearchPatient>

    /**
     * Searches the database, using % (with SQLite concatenation, ||) to make sure we search for
     * the names or IDs where the query is contained inside of them.
     *
     * The result will be sorted in descending order by latest reading date, and then by name (more
     * applicable for patients without a reading). Patients that don't have a reading will be
     * placed below all other patients that have readings.
     */
    @Query(
        """
SELECT * FROM LocalSearchPatient
WHERE
  name LIKE '%' || :query || '%'
  OR id LIKE '%' || :query || '%'
ORDER BY latestReadingDate DESC, name COLLATE NOCASE ASC
"""
    )
    fun localSearchPatientsByNameOrId(query: String): PagingSource<Int, LocalSearchPatient>

    /**
     * Query the database for all the patients that have been created or edited offline
     */
    @Query("SELECT * FROM Patient WHERE $patientsToUploadQueryCriteria")
    suspend fun readPatientsToUpload(): List<Patient>

    /**
     * Query the database for the number of patients that have been created or edited offline
     */
    @Query("SELECT COUNT(id) FROM Patient WHERE $patientsToUploadQueryCriteria")
    suspend fun countPatientsToUpload(): Int

    /**
     * get a list of patient Ids
     */
    @Query("SELECT id FROM Patient")
    suspend fun getPatientIdsList(): List<String>

    /**
     * get a single patient by id if exists
     */
    @Query("SELECT * FROM Patient WHERE id = :id")
    suspend fun getPatientById(id: String): Patient?

    /**
     * Gets the patient along with all of its readings if it exists.
     */
    @Transaction
    @Query("SELECT * FROM Patient WHERE id = :id")
    suspend fun getPatientAndReadingsById(id: String): PatientAndReadings?

    /**
     * delete all patients
     */
    @Query("DELETE FROM Patient")
    suspend fun deleteAllPatients()

    companion object {
        private const val patientsToUploadQueryCriteria = """
            lastServerUpdate IS NULL
            OR lastServerUpdate < lastEdited
            OR prevPregnancyEndDate IS NOT NULL
            OR medicalLastEdited IS NOT NULL
            OR drugLastEdited IS NOT NULL
            OR (gestationalAge IS NOT NULL AND pregnancyId IS NULL)
        """
    }
}
