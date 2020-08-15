package com.cradle.neptune.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading

/**
 * Data Access Object (DAO) for [Reading] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface ReadingDaoAccess {
    /**
     * Inserts a new reading into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param reading The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReading(reading: Reading)

    /**
     * Inserts each reading in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param readingEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(readingEntities: List<Reading>)

    /**
     * Updates an existing reading in the database.
     *
     * @param reading An entity containing updated data.
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(reading: Reading)

    /**
     * Removes an entity from the database.
     *
     * @param reading The entity to remove.
     */
    @Delete
    fun delete(reading: Reading?)

    /**
     * All of the readings in the database.
     */
    @get:Query("SELECT * FROM Reading")
    val allReadingEntities: List<Reading>

    /**
     * Returns the first reading who's id matches a given pattern.
     *
     * Note that this method does not perform an exact match on the reading id
     * and instead performs an SQL `LIKE` operation limiting the result to 1.
     *
     * @param id The reading id to search for.
     */
    @Query("SELECT * FROM Reading WHERE readingId LIKE :id LIMIT 1")
    fun getReadingById(id: String): Reading?

    /**
     * Returns all of the readings associated with a specified patient.
     *
     * @param id The id of the patient to find readings for.
     */
    @Query("SELECT * FROM Reading WHERE patientId LIKE :id")
    fun getAllReadingByPatientId(id: String): List<Reading>

    /**
     * All readings which have not yet been uploaded to the server.
     */
    @get:Query("SELECT * FROM Reading WHERE isUploadedToServer = 0")
    val allUnUploadedReading: List<Reading>

    /**
     * Returns all un-uploaded readings for patients who have previously been
     * synced with the server.
     */
    @get:Query(
        """
        SELECT * 
        FROM Reading r 
        JOIN Patient p ON r.patientId like p.id
        WHERE p.base is NOT null AND r.isUploadedToServer = 0
    """
    )
    val allUnUploadedReadingsForTrackedPatients: List<Reading>

    /**
     * get the newest reading of a particular patient
     */
    @Query("SELECT * FROM READING WHERE patientId LIKE :id ORDER BY dateTimeTaken LIMIT 1 ")
    fun getNewestReadingByPatientId(id: String): Reading?

    /**
     * Deletes all readings from the database.
     */
    @Query("DELETE FROM Reading")
    fun deleteAllReading()
}

@Dao
interface PatientDaoAccess {

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
    @get:Query("SELECT * FROM Patient")
    val allPatients: List<Patient>

    /**
     * Gets all patients along with their readings.
     */
    @get:Transaction
    @get:Query("SELECT * FROM Patient")
    val allPatientsAndReading: List<PatientAndReadings>

    @get:Transaction
    @get:Query("SELECT * FROM Patient WHERE base IS null")
    val unUploadedPatientAndReadings: List<PatientAndReadings>

    /**
     * get patients that were edited after a given timestamp and are not brand new patients
     */
    @Query("SELECT * FROM Patient WHERE lastEdited =:unixTime AND base IS NOT null")
    fun getEditedPatients(unixTime: Long): List<Patient>

    /**
     * get a list of patient Ids
     */
    @get:Query("SELECT id FROM Patient")
    val patientIdsList: List<String>

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

/**
 * Data Access Object (DAO) for [HealthFacility] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface HealthFacilityDaoAccess {
    /**
     * Inserts a new health facility into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param healthFacility The health facility to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(healthFacility: HealthFacility)

    /**
     * Inserts each health facility in the supplied list into the database.
     *
     * @param healthFacilities The list of facilities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(healthFacilities: List<HealthFacility>)

    /**
     * Updates an existing health facility.
     *
     * @param healthFacility An entity containing updated data.
     */
    @Update
    fun update(healthFacility: HealthFacility)

    /**
     * Removes an entity from the database.
     *
     * @param healthFacility The entity to remove.
     */
    @Delete
    fun delete(healthFacility: HealthFacility)

    /**
     * Deletes all health centres in the database.
     */
    @Query("DELETE FROM HealthFacility")
    fun deleteAll()

    /**
     * All health facilities stored in the database.
     */
    @get:Query("SELECT * FROM HealthFacility")
    val allHealthFacilities: List<HealthFacility>

    /**
     * Returns the first health facility from the database who's id matches
     * the supplied pattern.
     *
     * @param id The id of the health facility to retrieve.
     */
    @Query("SELECT * FROM HealthFacility WHERE id LIKE :id LIMIT 1")
    fun getHealthFacilityById(id: String): HealthFacility?

    /**
     * All health facilities which the user has selected to be visible.
     */
    @get:Query("SELECT * FROM HealthFacility WHERE isUserSelected = 1")
    val allUserSelectedHealthFacilities: List<HealthFacility>

    /**
     * Returns a live list of facilities
     */
    @get:Query("SELECT * FROM HealthFacility")
    val allFacilitiesLiveData: LiveData<List<HealthFacility>>
}
