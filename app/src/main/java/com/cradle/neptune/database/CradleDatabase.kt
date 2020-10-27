package com.cradle.neptune.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading

/**
 * Interface for the local cradle database.
 */
@Database(
    entities = [Reading::class, Patient::class, HealthFacility::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class CradleDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDaoAccess
    abstract fun patientDao(): PatientDaoAccess
    abstract fun healthFacility(): HealthFacilityDaoAccess


    companion object {
        private const val DATABASE_NAME = "room-readingDB"

        /**
         * Reference to hold the singleton. Marked with [Volatile] to prevent synchronization issues
         * from caching.
         *
         * ref: https://github.com/android/sunflower/blob/69472521b0e0dea9ffb41db223d6ee1cb27bd557/
         * app/src/main/java/com/google/samples/apps/sunflower/data/AppDatabase.kt#L41-L63
         */
        @Volatile
        private var instance: CradleDatabase? = null

        fun getInstance(context: Context): CradleDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, CradleDatabase::class.java, DATABASE_NAME).build()
    }
}
