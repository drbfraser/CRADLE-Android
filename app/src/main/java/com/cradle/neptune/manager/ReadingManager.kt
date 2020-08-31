package com.cradle.neptune.manager

import com.cradle.neptune.database.ReadingDaoAccess
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.RetestGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
 *
 *Added [suspend] function so that there is compile time error when inserting on DB through
 * main thread rather than run time error
 */
@Suppress("RedundantSuspendModifier")
class ReadingManager(private val daoAccess: ReadingDaoAccess) {

    /**
     * Adds a new reading to the database.
     * @param reading the reading to insert
     * todo once all the class using this api is converted to kotlin, we can move coroutine out
     * of this class and make this a [suspend] function
     */
    fun addReading(reading: Reading) {
        GlobalScope.launch {
            daoAccess.insertReading(reading)
        }
    }

    /**
     * Get all the readings.
     * todo once all the class using this api is converted to kotlin, we can move coroutine out
     * of this class and make this a [suspend] function
     */
    fun addAllReadings(readings: List<Reading>) {
        GlobalScope.launch {
            daoAccess.insertAll(readings)
        }
    }

    /**
     * Updates an existing reading in the database.
     * todo once all the class using this api is converted to kotlin, we can move coroutine out
     * of this class and make this a [suspend] function
     * @param reading the reading to update
     */
    fun updateReading(reading: Reading) = GlobalScope.launch { daoAccess.update(reading) }

    /**
     * Returns a list of all readings (and their associated patients) in the
     * database.
     */
    suspend fun getAllReadings(): List<Reading> {
        return daoAccess.allReadingEntities
    }

    /**
     * Returns the reading (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a reading.
     */
    suspend fun getReadingById(id: String): Reading? {
        return daoAccess.getReadingById(id)
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    fun getReadingByIdBlocking(id: String): Reading? {
        return runBlocking { withContext(Dispatchers.IO) { getReadingById(id) } }
    }

    /**
     * Returns all readings associated with a specific patient [id].
     */
    suspend fun getReadingsByPatientId(id: String): List<Reading> {
        return daoAccess.getAllReadingByPatientId(id)
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getReadingByPatientIdBlocking(id: String) = runBlocking {
        withContext(Dispatchers.IO) { getReadingsByPatientId(id) }
    }

    /**
     * Returns all readings which have not been uploaded to the server yet.
     */
    suspend fun getUnUploadedReadings(): List<Reading> {
        return daoAccess.allUnUploadedReading
    }

    /**
     * get unUploaded readings for patients who already exists in the server
     */
    suspend fun getUnUploadedReadingsForServerPatients(): List<Reading> {
        return daoAccess.allUnUploadedReadingsForTrackedPatients
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getUnUploadedReadingsBlocking() =
        runBlocking { withContext(Dispatchers.IO) { getUnUploadedReadings() } }

    /**
     * Constructs a [RetestGroup] for a given [reading].
     */
    suspend fun getRetestGroup(reading: Reading): RetestGroup {
        val readings = mutableListOf<Reading>()
        readings.addAll(reading.previousReadingIds.mapNotNull { getReadingById(it) })
        readings.add(reading)
        return RetestGroup(readings)
    }

    /**
     * Deletes the reading with a specific [id] from the database.
     */
    suspend fun deleteReadingById(id: String) {
        return daoAccess.delete(getReadingById(id))
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun deleteReadingByIdBlocking(id: String) =
        runBlocking { withContext(Dispatchers.IO) { deleteReadingById(id) } }

    /**
     * Get the newest reading of a patient
     */
    suspend fun getNewestReadingByPatientId(id: String): Reading? =
        daoAccess.getNewestReadingByPatientId(id)

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getNewestReadingByPatientIdBlocking(id: String) = runBlocking {
        withContext(Dispatchers.IO) { getNewestReadingByPatientId(id) }
    }

    /**
     * Deletes all readings from the database.
     */
    suspend fun deleteAllData() {
        daoAccess.deleteAllReading()
    }
}
