package com.cradleVSA.neptune.manager

import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.Referral
import com.cradleVSA.neptune.model.RetestGroup
import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.withContext
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
class ReadingManager constructor(
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
        when (result) {
            is Success -> {
                reading.isUploadedToServer = true
                readingDao.update(reading)
            }
        }
        return result.map { }
    }

    /**
     * downloads the reading from the server and save it to the local database
     * @return upload result.
     */
    suspend fun downloadNewReadingFromServer(id: String): NetworkResult<Unit> {
        val result = restApi.getReading(id)
        if (result is Success) {
            addReading(result.value, isReadingFromServer = true)
        }
        return result.map { Unit }
    }

    suspend fun addAssessment(assessment: Assessment) {
        getReadingById(assessment.readingId)?.apply {
            followUp = assessment
            referral?.isAssessed = true
            updateReading(this)
        }
    }

    suspend fun addReferral(referral: Referral) {
        readingDao.updateReferral(referral.readingId, referral)
    }

    suspend fun downloadAssessment(assessmentId: String): NetworkResult<Unit> {
        val result = restApi.getAssessment(assessmentId)
        if (result is Success) {
            addAssessment(result.value)
        }
        return result.map { }
    }

    suspend fun setLastEdited(readingId: String, lastEdited: Long) {
        readingDao.setLastEdited(readingId, lastEdited)
    }

    suspend fun setDateRecheckVitalsNeededToNull(readingId: String) {
        readingDao.setDateRecheckVitalsNeededToNull(readingId)
    }

    suspend fun setIsUploadedToServerToZero(readingId: String) {
        readingDao.setIsUploadedToServerToZero(readingId)
    }
}
