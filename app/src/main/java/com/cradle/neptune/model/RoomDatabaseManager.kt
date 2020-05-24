package com.cradle.neptune.model

import android.content.Context
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityEntity
import org.threeten.bp.ZonedDateTime
import java.util.UUID

class RoomDatabaseManager(val database: CradleDatabase) : ReadingManager {

    private val readingDao get() = database.readingDaoAccess()
    private val healthFacilityDao get() = database.healthFacilityDaoAccess()

    /**
     * Adds a new reading to the database.
     */
    override fun addNewReading(context: Context, reading: Reading) {
        reading.metadata.dateLastSaved = ZonedDateTime.now()

        // Since this is the first time we are adding this reading to the
        // database it probably does not have an id so we'll give it one.
        val entity = reading.toEntity()
        readingDao.insertReading(entity)

        // Update other readings associated with this patient.
        for (r in getReadingByPatientID(context, reading.patientId)) {
            if (r.isVitalRecheckRequired) {
                r.dateRecheckVitalsNeeded = null
                updateReading(context, r)
            }
        }
    }

    override fun updateReading(context: Context, reading: Reading) {
        reading.metadata.dateLastSaved = ZonedDateTime.now()

        // We assume that the reading already exists in the database and, as
        // such, has an id. If it doesn't then that is a programmer error and
        // we crash with a null pointer exception.
        val entity = reading.toEntity()
        readingDao.update(entity)
    }

    /**
     * Retrieves all readings from the database.
     */
    override fun getReadings(context: Context): List<Reading> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the reading with a given [id].
     */
    override fun getReadingById(context: Context, id: String): Reading? {
        return getReadingById(id)
    }

    private fun getReadingById(id: String): Reading? {
        val entity = readingDao.getReadingById(id) ?: return null
        val jsonString = entity.readDataJsonString!!
        val json = JsonObject(jsonString)
        return Reading.unmarshal(json)
    }

    /**
     * Constructs a [RetestGroup] for [reading] by grouping together all
     * related readings.
     */
    override fun getRetestGroup(reading: Reading): RetestGroup {
        val readings = mutableListOf<Reading>()
        readings.addAll(reading.previousReadingIds.mapNotNull { id -> this.getReadingById(id) })
        readings.add(reading)
        return RetestGroup(readings)
    }

    /**
     * Deletes the reading with a given [id].
     *
     * If no such reading exists in the database, then this method does nothing.
     */
    override fun deleteReadingById(context: Context, id: String) {
        val entity = readingDao.getReadingById(id) ?: return
        readingDao.delete(entity)
    }

    /**
     * Deletes all readings from the database.
     */
    override fun deleteAllData(context: Context) {
        readingDao.deleteAllReading()
    }

    // TODO: Rename to getReadingsByPatientId to be consistent
    override fun getReadingByPatientID(context: Context, id: String): List<Reading> {
        TODO("Not yet implemented")
    }

    /**
     * Inserts all supplied readings into the database.
     *
     * If any of the readings do not have identifiers, new ones are generated
     * for them before they are inserted into the database.
     */
    override fun addAllReadings(context: Context, readings: List<Reading>) =
        readings.map(Reading::toEntity).forEach(readingDao::insertReading)

    override fun getUnuploadedReadings(): List<Reading> = readingDao.allUnUploadedReading
        .map {
            // The entity's json string field may not be `null`, the only
            // reason it is declared optional is to conform to the legacy
            // database schema.
            val json = JsonObject(it.readDataJsonString!!)
            Reading.unmarshal(json)
        }

    override fun insert(healthFacilityEntity: HealthFacilityEntity) =
        healthFacilityDao.insert(healthFacilityEntity)

    override fun removeFacilityById(id: String) {
        val facility = healthFacilityDao.getHealthFacilityById(id) ?: return
        healthFacilityDao.delete(facility)
    }

    override fun insertAll(facilities: List<HealthFacilityEntity>) =
        healthFacilityDao.insertAll(facilities)

    override fun getAllFacilities() = healthFacilityDao.allHealthFacilities

    override fun getFacilityById(id: String) = healthFacilityDao.getHealthFacilityById(id)

    override fun getUserSelectedFacilities() = healthFacilityDao.allUserSelectedHealthFacilities

    override fun updateFacility(healthFacilityEntity: HealthFacilityEntity) =
        healthFacilityDao.update(healthFacilityEntity)
}
