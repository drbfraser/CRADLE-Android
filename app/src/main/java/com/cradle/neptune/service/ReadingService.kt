package com.cradle.neptune.service

import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.RetestGroup
import kotlinx.coroutines.*

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
interface ReadingService {

    /**
     * Adds a new reading to the database.
     *
     * Due to how the database schema is setup, if we also need to supply
     * patient information whenever we want to create a new reading.
     *
     * @param patient the patient associated with the reading
     * @param reading the reading to insert
     */
    suspend fun addReading(patient: Patient, reading: Reading)

    /**
     * Updates an existing reading in the database.
     *
     * Due to how the database schema is setup, we need to supply patient
     * information along with the reading we wish to update.
     *
     * @param patient the patient associated with the reading
     * @param reading the reading to update
     */
    suspend fun updateReading(patient: Patient, reading: Reading)

    /**
     * Returns a list of all readings (and their associated patients) in the
     * database.
     */
    suspend fun getAllReadings(): List<Pair<Patient, Reading>>

    /**
     * Returns the reading (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a reading.
     */
    suspend fun getReadingById(id: String): Pair<Patient, Reading>?

    /**
     * Returns all readings associated with a specific patient [id].
     */
    suspend fun getReadingsByPatientId(id: String): List<Pair<Patient, Reading>>

    /**
     * Returns all readings which have not been uploaded to the server yet.
     */
    suspend fun getUnUploadedReadings(): List<Pair<Patient, Reading>>

    /**
     * Constructs a [RetestGroup] for a given [reading].
     */
    suspend fun getRetestGroup(reading: Reading): RetestGroup

    /**
     * Deletes the reading with a specific [id] from the database.
     */
    suspend fun deleteReadingById(id: String)

    /**
     * Deletes all readings from the database.
     */
    suspend fun deleteAllData()

    /**
     * Async variant of [addReading].
     */
    fun addReadingAsync(patient: Patient, reading: Reading) = GlobalScope.launch(Dispatchers.IO) {
        addReading(patient, reading)
    }

    /**
     * Async variant of [updateReading].
     */
    fun updateReadingAsync(patient: Patient, reading: Reading) = GlobalScope.launch(Dispatchers.IO) {
        updateReading(patient, reading)
    }

    /**
     * Async variant of [getAllReadings].
     */
    fun getAllReadingsAsync() = GlobalScope.async(Dispatchers.IO) { getAllReadings() }

    /**
     * Blocking variant of [getAllReadings].
     */
    fun getAllReadingsBlocking() = runBlocking { getAllReadings() }

    /**
     * Async variant of [getReadingById].
     */
    fun getReadingByIdAsync(id: String) = GlobalScope.async(Dispatchers.IO) { getReadingById(id) }

    /**
     * Blocking variant of [getReadingById].
     */
    fun getReadingByIdBlocking(id: String) = runBlocking { getReadingById(id) }

    /**
     * Async variant of [getReadingsByPatientId].
     */
    fun getReadingsByPatientIdAsync(id: String) = GlobalScope.async(Dispatchers.IO) { getReadingsByPatientId(id) }

    /**
     * Blocking variant of [getReadingsByPatientId].
     */
    fun getReadingsByPatientIdBlocking(id: String) = runBlocking { getReadingsByPatientId(id) }

    /**
     * Async variant of [getUnUploadedReadings].
     */
    fun getUnUploadedReadingsAsync() = GlobalScope.async(Dispatchers.IO) { getUnUploadedReadings() }

    /**
     * Blocking variant of [getUnUploadedReadings].
     */
    fun getUnUploadedReadingsBlocking() = runBlocking { getUnUploadedReadings() }

    /**
     * Async variant of [getRetestGroup].
     */
    fun getRetestGroupAsync(reading: Reading) = GlobalScope.async(Dispatchers.IO) { getRetestGroup(reading) }

    /**
     * Blocking variant of [getRetestGroup].
     */
    fun getRetestGroupBlocking(reading: Reading) = runBlocking { getRetestGroup(reading) }

    /**
     * Async variant of [deleteReadingById].
     */
    fun deleteReadingByIdAsync(id: String) = GlobalScope.async(Dispatchers.IO) { deleteReadingById(id) }

    /**
     * Async variant of [deleteAllData].
     */
    fun deleteAllDataAsync() = GlobalScope.async(Dispatchers.IO) { deleteAllData() }
}
