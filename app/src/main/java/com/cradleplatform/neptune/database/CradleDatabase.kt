package com.cradleplatform.neptune.database

import android.content.Context
import androidx.room.Database
import androidx.room.Index
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.HealthFacilityDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.ext.jackson.writeBooleanField
import com.cradleplatform.neptune.ext.jackson.writeIntField
import com.cradleplatform.neptune.ext.jackson.writeLongField
import com.cradleplatform.neptune.ext.jackson.writeOptIntField
import com.cradleplatform.neptune.ext.jackson.writeOptLongField
import com.cradleplatform.neptune.ext.jackson.writeOptStringField
import com.cradleplatform.neptune.ext.jackson.writeStringField
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral

const val CURRENT_DATABASE_VERSION = 2

/**
 * An interface for the local CRADLE database.
 *
 * About migrations: Although we want to prioritize the data that is on the server, we don't want
 * to fall back to a destructive migration in case the user has un-uploaded [Patient]s or [Reading]s
 *
 * TODO: Lower the version back to version 1 when the app is out of alpha testing.
 */
@Database(
    entities = [Reading::class, Patient::class, HealthFacility::class, Referral::class, Assessment::class],
    views = [LocalSearchPatient::class],
    version = CURRENT_DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class CradleDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun patientDao(): PatientDao
    abstract fun healthFacility(): HealthFacilityDao
    abstract fun referralDao(): ReferralDao
    abstract fun assessmentDao(): AssessmentDao

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

        // Suppress SpreadOperator, because ALL_MIGRATIONS is only used during migration and only
        // done on app startup, so the array copy risk is minimal here.
        @Suppress("SpreadOperator")
        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, CradleDatabase::class.java, DATABASE_NAME)
                .addMigrations(*Migrations.ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

/**
 * Internal so that tests can access the [Migration] objects.
 * Suppress MagicNumber and NestedBlockDepth due to the migrations.
 */
@Suppress("MagicNumber", "NestedBlockDepth", "ObjectPropertyNaming")
internal object Migrations {
    val ALL_MIGRATIONS: Array<Migration> by lazy {
        arrayOf(MIGRATION_1_2)
    }
    /**
     * Version 2:
     * Create Referral and Assessment table
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.apply {
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Referral (
                        `id` INTEGER NOT NULL, 
                        `comment` TEXT NULLABLE,
                        `referralHealthFacilityName` TEXT NOT NULL, 
                        `dateReferred` LONG NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `actionTaken` TEXT NULLABLE,
                        `cancelReason` TEXT NULLABLE,
                        `notAttendReason` TEXT NULLABLE,
                        `isAssessed` BOOLEAN NOT NULL,
                        `isCancelled` BOOLEAN NOT NULL,
                        `notAttended` BOOLEAN NOT NULL,
                        `lastEdited` LONG NOT NULL,
                        `lastServerUpdate` LONG NULLABLE,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`referralHealthFacilityName`) REFERENCES `HealthFacility`(`name`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()

                )

                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Assessment (
                        `id` INTEGER NULLABLE, 
                        `dateAssessed` LONG NOT NULL,
                        `healthcareWorkerId` INTEGER NOT NULL, 
                        `patientId` TEXT NOT NULL, 
                        `diagnosis` TEXT NULLABLE, 
                        `treatment` TEXT NULLABLE, 
                        `medicationPrescribed` TEXT NULLABLE, 
                        `specialInvestigations` TEXT NULLABLE, 
                        `followupNeeded` BOOLEAN NULLABLE,
                        `followupInstructions` TEXT NULLABLE, 
                        `lastEdited` LONG NOT NULL,
                        `lastServerUpdate` LONG NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Referral_id` ON `Referral` (`id`, `patientId`, `healthFacilityName`)")
                execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Assessment_id` ON `Assessment` (`id`, `patientId`)")
            }
        }
    }
}
