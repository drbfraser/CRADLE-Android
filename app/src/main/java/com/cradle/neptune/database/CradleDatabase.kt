package com.cradle.neptune.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Interface for the local cradle database.
 */
@Database(
    entities = [ReadingEntity::class, PatientEntity::class,HealthFacilityEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CradleDatabase : RoomDatabase() {
    /**
     * Returns the data access object for [ReadingEntity] entities.
     */
    abstract fun readingDaoAccess(): ReadingDaoAccess

    /**
     * Return the data access object for [PatientEntity] entities
     */
    abstract fun patientDaoAccess(): PatientDaoAccess

    /**
     * Returns the data access object for [HealthFacilityEntity] entities.
     */
    abstract fun healthFacilityDaoAccess(): HealthFacilityDaoAccess
}
