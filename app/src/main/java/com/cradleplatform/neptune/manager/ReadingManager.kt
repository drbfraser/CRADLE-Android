package com.cradleplatform.neptune.manager

import androidx.room.withTransaction
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.RetestGroup
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.net.map
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
 */
@Singleton
class ReadingManager @Inject constructor(
    private val database: CradleDatabase,
    private val readingDao: ReadingDao,
    private val restApi: RestApi
) {

    /**
     * Adds a new reading to the database.
     * @param reading the reading to insert
     */
    suspend fun addReading(reading: Reading, isReadingFromServer: Boolean) {
        if (isReadingFromServer) reading.isUploadedToServer = true

        readingDao.updateOrInsertIfNotExists(reading)
    }

    /**
     * Get all the readings.
     */
    suspend fun addAllReadings(readings: List<Reading>) = readingDao.insertAll(readings)

    /**
     * Updates an existing reading in the database.
     * of this class and make this a [suspend] function
     * @param reading the reading to update
     */
    suspend fun updateReading(reading: Reading) = readingDao.update(reading)

    /**
     * Returns the reading (and its associated patient) with a given [id] from
     * the database. Returns `null` if unable to find such a reading.
     */
    suspend fun getReadingById(id: String): Reading? = readingDao.getReadingById(id)

    /**
     * Returns all readings associated with a specific patient [id].
     */
    suspend fun getReadingsByPatientId(id: String): List<Reading> =
        readingDao.getAllReadingByPatientId(id)

    /**
     * Returns all readings which have not been uploaded to the server yet.
     */
    suspend fun getUnUploadedReadings(): List<Reading> = readingDao.getAllUnUploadedReadings()

    suspend fun markAllReadingsAsUploaded() = readingDao.markAllAsUploadedToServer()

    /**
     * get unUploaded readings for patients who already exists in the server
     */
    suspend fun getUnUploadedReadingsForServerPatients(): List<Reading> =
        readingDao.getAllUnUploadedReadingsForTrackedPatients()

    /**
     * Constructs a [RetestGroup] for a given [reading].
     * [reading] will be included in the resulting [RetestGroup].
     */
    suspend fun createRetestGroup(reading: Reading): RetestGroup = withContext(Default) {
        val readings = readingDao.getReadingsByIds(reading.previousReadingIds).toMutableList()
        if (reading.id !in reading.previousReadingIds) readings.add(reading)
        return@withContext RetestGroup(readings)
    }

    /**
     * Deletes the reading with a specific [id] from the database.
     */
    suspend fun deleteReadingById(id: String) = readingDao.delete(getReadingById(id))

    /**
     * Get the newest reading of a patient
     */
    suspend fun getNewestReadingByPatientId(id: String): Reading? =
        readingDao.getNewestReadingByPatientId(id)

    /**
     * upload new reading to the server and mark it uploaded based on the result.
     * @return upload result
     */
    suspend fun uploadNewReadingToServer(reading: Reading): NetworkResult<Unit> {
        val result = restApi.postReading(reading)
        if (result is NetworkResult.Success) {
            reading.isUploadedToServer = true
            readingDao.update(reading)
        }
        return result.map { }
    }

    /**
     * downloads the reading from the server and save it to the local database
     * @return upload result.
     */
    suspend fun downloadNewReadingFromServer(id: String): NetworkResult<Unit> {
        val result = restApi.getReading(id)
        if (result is NetworkResult.Success) {
            addReading(result.value, isReadingFromServer = true)
        }
        return result.map { Unit }
    }

    /**
     * @param lastEdited Optional: If not null, it will set the lastEdited column in the database
     * to the value.
     */
    suspend fun clearDateRecheckVitalsAndMarkForUpload(
        readingId: String,
        lastEdited: Long? = null
    ) {
        database.withTransaction {
            readingDao.apply {
                lastEdited?.let { setLastEdited(readingId, it) }
                setDateRecheckVitalsNeededToNull(readingId)
                setIsUploadedToServerToZero(readingId)
            }
        }
    }
}
