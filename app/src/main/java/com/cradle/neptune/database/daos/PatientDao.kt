package com.cradle.neptune.database.daos

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.cradle.neptune.database.views.LocalSearchPatient
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings

@Dao
interface PatientDao {

    /**
     * inserts a patient into patient table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(patient: Patient)

    /**
     * insert a list of patients into the patient table
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(patients: List<Patient>)

    /**
     * Deletes a patient from the patient table.
     */
    @Delete
    fun delete(patient: Patient)

    /**
     *get list of all the patients
     */
    @Query("SELECT * FROM Patient")
    fun getAllPatients(): List<Patient>

    @Query(
        "SELECT * FROM LocalSearchPatient " +
            "ORDER BY latestReadingDate DESC, name COLLATE NOCASE ASC"
    )
    fun allLocalSearchPatientsByDate(): PagingSource<Int, LocalSearchPatient>

    /**
     * Searches the database, using % (with SQLite concatenation, ||) to make sure we search for
     * the names or IDs where the query is contained inside of them.
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
     * Gets all patients along with their readings.
     */
    @Transaction
    @Query("SELECT * FROM Patient")
    fun getAllPatientsAndReading(): List<PatientAndReadings>

    @Transaction
    @Query("SELECT * FROM Patient WHERE base IS null")
    fun getUnUploadedPatientAndReadings(): List<PatientAndReadings>

    /**
     * get patients that were edited after a given timestamp and are not brand new patients
     */
    @Query("SELECT * FROM Patient WHERE lastEdited =:unixTime AND base IS NOT null")
    fun getEditedPatients(unixTime: Long): List<Patient>

    /**
     * get a list of patient Ids
     */
    @Query("SELECT id FROM Patient")
    fun getPatientIdsList(): List<String>

    /**
     * get a single patient by id if exists
     */
    @Query("SELECT * FROM Patient WHERE id LIKE :id LIMIT 1")
    fun getPatientById(id: String): Patient?

    /**
     * Gets the patient along with all of its readings if it exists.
     */
    @Transaction
    @Query("SELECT * FROM Patient WHERE id LIKE :id LIMIT 1")
    fun getPatientAndReadingsById(id: String): PatientAndReadings?

    /**
     * delete all patients
     */
    @Query("DELETE FROM Patient")
    fun deleteAllPatients()
}