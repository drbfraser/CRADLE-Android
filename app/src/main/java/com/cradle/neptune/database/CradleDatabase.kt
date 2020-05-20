package com.cradle.neptune.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Interface for the local cradle database.
 */
@Database(
    entities = [ReadingEntity::class, HealthFacilityEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CradleDatabase : RoomDatabase() {
    /**
     * Returns the data access object for [ReadingEntity] entities.
     */
    abstract fun readingDaoAccess(): ReadingDaoAccess

    /**
     * Returns the data access object for [HealthFacilityEntity] entities.
     */
    abstract fun healthFacilityDaoAccess(): HealthFacilityDaoAccess
}
