package com.cradle.neptune.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cradle.neptune.model.Patient

/**
 * Data Access Object (DAO) for [ReadingEntity] entities.
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
     * @param readingEntity The entity to insert into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReading(readingEntity: ReadingEntity)

    /**
     * Inserts each reading in the supplied list into the database.
     *
     * If a conflicting element already exists in the database it will be
     * replaced with the new one.
     *
     * @param readingEntities A list of entities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(readingEntities: List<ReadingEntity>)

    /**
     * Updates an existing reading in the database.
     *
     * @param readingEntity An entity containing updated data.
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(readingEntity: ReadingEntity)

    /**
     * Removes an entity from the database.
     *
     * @param readingEntity The entity to remove.
     */
    @Delete
    fun delete(readingEntity: ReadingEntity)

    /**
     * All of the readings in the database.
     */
    @get:Query("SELECT * FROM ReadingEntity")
    val allReadingEntities: List<ReadingEntity>

    /**
     * Returns the first reading who's id matches a given pattern.
     *
     * Note that this method does not perform an exact match on the reading id
     * and instead performs an SQL `LIKE` operation limiting the result to 1.
     *
     * @param id The reading id to search for.
     */
    @Query("SELECT * FROM ReadingEntity WHERE readingId LIKE :id LIMIT 1")
    fun getReadingById(id: String): ReadingEntity?

    /**
     * Returns all of the readings associated with a specified patient.
     *
     * @param id The id of the patient to find readings for.
     */
    @Query("SELECT * FROM ReadingEntity WHERE patientId LIKE :id")
    fun getAllReadingByPatientId(id: String): List<ReadingEntity>

    /**
     * All readings which have not yet been uploaded to the server.
     */
    @get:Query("SELECT * FROM ReadingEntity WHERE isUploadedToServer = 0")
    val allUnUploadedReading: List<ReadingEntity>

    /**
     * Deletes all readings from the database.
     */
    @Query("DELETE FROM ReadingEntity")
    fun deleteAllReading()
}

@Dao
interface PatientDaoAccess {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(patient: Patient)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(patients: List<Patient>)

    @Delete
    fun delete(patient: Patient)

    @get:Query("SELECT * FROM Patient")
    val allPatients: List<Patient>

    @Query("SELECT * FROM Patient WHERE id LIKE :id LIMIT 1")
    fun getPatientById(id: String): Patient?

    @Query("DELETE FROM ReadingEntity")
    fun deleteAllPatients()
}

/**
 * Data Access Object (DAO) for [HealthFacilityEntity] entities.
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
     * @param healthFacilityEntity The health facility to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(healthFacilityEntity: HealthFacilityEntity)

    /**
     * Inserts each health facility in the supplied list into the database.
     *
     * @param healthFacilityEntities The list of facilities to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(healthFacilityEntities: List<HealthFacilityEntity>)

    /**
     * Updates an existing health facility.
     *
     * @param healthFacilityEntity An entity containing updated data.
     */
    @Update
    fun update(healthFacilityEntity: HealthFacilityEntity)

    /**
     * Removes an entity from the database.
     *
     * @param healthFacilityEntity The entity to remove.
     */
    @Delete
    fun delete(healthFacilityEntity: HealthFacilityEntity)

    /**
     * Deletes all health centres in the database.
     */
    @Query("DELETE FROM HealthFacilityEntity")
    fun deleteAll()

    /**
     * All health facilities stored in the database.
     */
    @get:Query("SELECT * FROM HealthFacilityEntity")
    val allHealthFacilities: List<HealthFacilityEntity>

    /**
     * Returns the first health facility from the database who's id matches
     * the supplied pattern.
     *
     * @param id The id of the health facility to retrieve.
     */
    @Query("SELECT * FROM HealthFacilityEntity WHERE id LIKE :id LIMIT 1")
    fun getHealthFacilityById(id: String): HealthFacilityEntity?

    /**
     * All health facilities which the user has selected to be visible.
     */
    @get:Query("SELECT * FROM HealthFacilityEntity WHERE isUserSelected = 1")
    val allUserSelectedHealthFacilities: List<HealthFacilityEntity>
}
