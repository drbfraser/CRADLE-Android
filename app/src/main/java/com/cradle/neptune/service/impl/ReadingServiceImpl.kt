package com.cradle.neptune.service.impl

import android.util.Log
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.ReadingEntity
import com.cradle.neptune.model.*
import com.cradle.neptune.service.MarshalService
import com.cradle.neptune.service.ReadingService
import org.threeten.bp.ZonedDateTime
import javax.inject.Inject

class ReadingServiceImpl @Inject constructor(
    private val database: CradleDatabase,
    private val marshalService: MarshalService
) : ReadingService {

    private val readingDao get() = database.readingDaoAccess()

    /**
     * Adds a reading/patient pair to the database.
     */
    override suspend fun addReading(patient: Patient, reading: Reading) {
        reading.metadata.dateLastSaved = ZonedDateTime.now()
        val entity = constructEntity(patient, reading)
        Log.d(this::class.qualifiedName, "Persisting reading: ${reading.id}")
        readingDao.insertReading(entity)
    }

    /**
     * Updates a given reading/patient pair in the database.
     */
    override suspend fun updateReading(patient: Patient, reading: Reading) {
        reading.metadata.dateLastSaved = ZonedDateTime.now()
        val entity = constructEntity(patient, reading)
        Log.d(this::class.qualifiedName, "Updating reading: ${reading.id}")
        readingDao.update(entity)
    }

    /**
     * Returns a list of all readings and their associated patients.
     */
    override suspend fun getAllReadings(): List<Pair<Patient, Reading>> =
        readingDao.allReadingEntities.mapNotNull { deconstructEntity(it) }

    /**
     * Returns the reading/patient pair with a specific reading id.
     */
    override suspend fun getReadingById(id: String): Pair<Patient, Reading>? {
        val entity = readingDao.getReadingById(id) ?: return null
        return deconstructEntity(entity)
    }

    /**
     * Returns all readings for a specific patient id.
     */
    override suspend fun getReadingsByPatientId(id: String): List<Pair<Patient, Reading>> =
        readingDao.getAllReadingByPatientId(id)
            .mapNotNull { deconstructEntity(it) }

    /**
     * Returns all readings which have not been uploaded to the server.
     */
    override suspend fun getUnUploadedReadings(): List<Pair<Patient, Reading>> =
        readingDao.allUnUploadedReading.mapNotNull { deconstructEntity(it) }

    /**
     * Constructs a [RetestGroup] for [reading] by pulling associated readings
     * from the database.
     */
    override suspend fun getRetestGroup(reading: Reading): RetestGroup {
        val readings = mutableListOf<Reading>()
        readings.addAll(reading.previousReadingIds.mapNotNull { getReadingById(it)?.second })
        readings.add(reading)
        return RetestGroup(readings)
    }

    /**
     * Deletes the reading with a given [id].
     *
     * If no such reading exists in the database, then this method does nothing.
     */
    override suspend fun deleteReadingById(id: String) {
        val entity = readingDao.getReadingById(id) ?: return
        readingDao.delete(entity)
    }

    /**
     * Deletes all readings from the database.
     */
    override suspend fun deleteAllData() = readingDao.deleteAllReading()

    /**
     * Composes a [Patient] and [Reading] into a [ReadingEntity].
     */
    private fun constructEntity(patient: Patient, reading: Reading): ReadingEntity {
        val json = reading.marshal()
        json.put(ReadingEntityField.PATIENT, patient)
        return ReadingEntity(reading.id, reading.patientId, json.toString(), reading.metadata.isUploaded)
    }

    /**
     * Decomposes a [ReadingEntity] into [Patient] and [Reading] models.
     *
     * Returns `null` if the entity does not contain JSON data. This should
     * never be the case so it is almost always safe to unwrap this result.
     */
    private fun deconstructEntity(entity: ReadingEntity): Pair<Patient, Reading>? {
        val jsonString = entity.readDataJsonString
        if (jsonString == null) {
            Log.d(this::class.qualifiedName, "Entity missing JSON data: ${entity.readingId}")
            return null
        }
        val json = JsonObject(jsonString)
        return marshalService.unmarshalDatabaseJson(json)
    }
}

private enum class ReadingEntityField(override val text: String) : Field {
    PATIENT("patient")
}