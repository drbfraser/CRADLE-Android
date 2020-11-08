package com.cradle.neptune.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cradle.neptune.model.HealthFacility

/**
 * Data Access Object (DAO) for [HealthFacility] entities.
 *
 * Provides methods for adding, updating, and removing entities from a database
 * along with a series of query methods.
 */
@Dao
interface HealthFacilityDao {
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
     * Deletes all health facilities in the database.
     */
    @Query("DELETE FROM HealthFacility")
    fun deleteAll()

    /**
     * All health facilities stored in the database.
     */
    @Query("SELECT * FROM HealthFacility")
    fun getAllHealthFacilities(): List<HealthFacility>

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
    @Query("SELECT * FROM HealthFacility WHERE isUserSelected = 1")
    fun getAllUserSelectedHealthFacilities(): List<HealthFacility>

    /**
     * Returns a live list of facilities
     */
    @Query("SELECT * FROM HealthFacility")
    fun getAllFacilitiesLiveData(): LiveData<List<HealthFacility>>

    /**
     * All health facilities which the user has selected to be visible.
     */
    @Query("SELECT * FROM HealthFacility WHERE isUserSelected = 1")
    fun getAllUserSelectedHealthFacilitiesLiveData(): LiveData<List<HealthFacility>>
}