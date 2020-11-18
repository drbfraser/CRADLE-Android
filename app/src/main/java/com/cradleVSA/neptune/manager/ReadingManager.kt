package com.cradleVSA.neptune.manager

import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.RetestGroup
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
class ReadingManager @Inject constructor(
    private val readingDao: ReadingDao,
    private val restApi: RestApi
) {

    /**
     * Adds a new reading to the database.
     * @param reading the reading to insert
     */
    suspend fun addReading(reading: Reading) = withContext(IO) { readingDao.insert(reading) }

    /**
     * Get all the readings.
     */
    suspend fun addAllReadings(readings: List<Reading>) = withContext(IO) {
        readingDao.insertAll(readings)
    }

    /**
     * Updates an existing reading in the database.
     * of this class and make this a [suspend] function
     * @param reading the reading to update
     */
    suspend fun updateReading(reading: Reading) = withContext(IO) { readingDao.update(reading) }

    /**
     * Returns a list of all readings (and their associated patients) in the
     * database.
     */
    @Deprecated(
        """
        Do not use this; this is memory inefficient. For stats, it should be done in the database
    """
    )
    suspend fun getAllReadings(): List<Reading> = withContext(IO) {
        readingDao.getAllReadingEntities()
    }

    /**
     * Returns the reading (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a reading.
     */
    suspend fun getReadingById(id: String): Reading? = withContext(IO) {
        readingDao.getReadingById(id)
    }

    /**
     * Returns all readings associated with a specific patient [id].
     */
    suspend fun getReadingsByPatientId(id: String): List<Reading> = withContext(IO) {
        readingDao.getAllReadingByPatientId(id)
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated(
        "Please avoid using this function in Kotlin files.",
        replaceWith = ReplaceWith("getReadingsByPatientId")
    )
    fun getReadingByPatientIdBlocking(id: String) = runBlocking {
        withContext(IO) { getReadingsByPatientId(id) }
    }

    /**
     * Returns all readings which have not been uploaded to the server yet.
     */
    suspend fun getUnUploadedReadings(): List<Reading> = withContext(IO) {
        readingDao.getAllUnUploadedReadings()
    }

    /**
     * get unUploaded readings for patients who already exists in the server
     */
    suspend fun getUnUploadedReadingsForServerPatients(): List<Reading> = withContext(IO) {
        readingDao.getAllUnUploadedReadingsForTrackedPatients()
    }

    /**
     * Constructs a [RetestGroup] for a given [reading].
     */
    suspend fun getRetestGroup(reading: Reading): RetestGroup = withContext(Default) {
        val readings = mutableListOf<Reading>()
        readings.addAll(reading.previousReadingIds.mapNotNull { getReadingById(it) })
        readings.add(reading)
        return@withContext RetestGroup(readings)
    }

    /**
     * Deletes the reading with a specific [id] from the database.
     */
    suspend fun deleteReadingById(id: String) = withContext(IO) {
        readingDao.delete(getReadingById(id))
    }

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated(
        "Please avoid using this function in Kotlin files.",
        replaceWith = ReplaceWith("deleteReadingById")
    )
    fun deleteReadingByIdBlocking(id: String) =
        runBlocking { withContext(IO) { deleteReadingById(id) } }

    /**
     * Get the newest reading of a patient
     */
    suspend fun getNewestReadingByPatientId(id: String): Reading? = withContext(IO) {
        readingDao.getNewestReadingByPatientId(id)
    }

    /**
     * Deletes all readings from the database.
     */
    suspend fun deleteAllData() = withContext(IO) { readingDao.deleteAllReading() }

    /**
     * upload new reading to the server and mark it uploaded based on the result.
     * @return upload result
     */
    suspend fun uploadNewReadingToServer(reading: Reading): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.postReading(reading)
            when (result) {
                is Success -> {
                    reading.isUploadedToServer = true
                    readingDao.update(reading)
                }
            }
            result.map { Unit }
        }

    /**
     * downloads the reading from the server and save it to the local database
     * @return upload result.
     */
    suspend fun downloadNewReadingFromServer(id: String): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.getReading(id)
            when (result) {
                is Success -> {
                    val reading = result.value.apply {
                        isUploadedToServer = true
                    }
                    addReading(reading)
                }
            }
            result.map { Unit }
        }

    suspend fun downloadAssessment(assessmentId: String): NetworkResult<Unit> =
        withContext(IO) {
            val result = restApi.getAssessment(assessmentId)
            when (result) {
                is Success -> {
                    getReadingById(result.value.readingId)?.apply {
                        followUp = result.value
                        referral?.isAssessed = true
                        updateReading(this)
                    }
                }
            }
            result.map { Unit }
        }
}
