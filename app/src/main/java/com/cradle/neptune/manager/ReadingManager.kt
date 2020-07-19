package com.cradle.neptune.manager

import com.cradle.neptune.database.ReadingDaoAccess
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.RetestGroup

/**
 * Service for interfacing with readings stored in the database.
 *
 * This service is used to abstract away the raw database schema which prefers
 * to store data as JSON instead of using actual database constructs.
 *
 * When interacting with this service from Kotlin, use of the `suspend` methods
 * is preferred. For Java interop, one can use either the convenience `Async`
 * or `Blocking` variants. For call-and-forget methods like [addReading], use
 * the `Async` variant. For methods which return a value, the `Blocking`
 * variants may be used but remember that those will block the current thread.
 */
class ReadingManager(private val daoAccess: ReadingDaoAccess) {

    /**
     * Adds a new reading to the database.
     *
     * Due to how the database schema is setup, if we also need to supply
     * patient information whenever we want to create a new reading.
     *
     * @param reading the reading to insert
     */
    fun addReading(reading: Reading){
        daoAccess.insertReading(reading)
    }

    /**
     * Updates an existing reading in the database.
     *
     * Due to how the database schema is setup, we need to supply patient
     * information along with the reading we wish to update.
     *
     * @param reading the reading to update
     */
     fun updateReading(reading: Reading){
        daoAccess.update(reading)
    }

    /**
     * Returns a list of all readings (and their associated patients) in the
     * database.
     */
    fun getAllReadings(): List<Reading>{
        return daoAccess.allReadingEntities
    }

    /**
     * Returns the reading (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a reading.
     */
    fun getReadingById(id: String): Reading?{
        return daoAccess.getReadingById(id)
    }

    /**
     * Returns all readings associated with a specific patient [id].
     */
    fun getReadingsByPatientId(id: String): List<Reading> {
        return daoAccess.getAllReadingByPatientId(id)
    }

    /**
     * Returns all readings which have not been uploaded to the server yet.
     */
    fun getUnUploadedReadings(): List<Reading> {
        return daoAccess.allUnUploadedReading
    }

    /**
     * Constructs a [RetestGroup] for a given [reading].
     */
    fun getRetestGroup(reading: Reading): RetestGroup {
        val readings = mutableListOf<Reading>()
        readings.addAll(reading.previousReadingIds.mapNotNull { getReadingById(it) })
        readings.add(reading)
        return RetestGroup(readings)
    }

    /**
     * Deletes the reading with a specific [id] from the database.
     */
    fun deleteReadingById(id: String) {
        return daoAccess.delete(getReadingById(id))
    }

    /**
     * Get the newest reading of a patient
     */
    fun getNewestReadingByPatientId(id: String) = daoAccess.getNewestReadingByPatientId(id)

    /**
     * Deletes all readings from the database.
     */
    fun deleteAllData(){
        daoAccess.deleteAllReading()
    }
}
