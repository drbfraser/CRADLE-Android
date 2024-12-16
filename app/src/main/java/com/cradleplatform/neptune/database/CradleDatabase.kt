package com.cradleplatform.neptune.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.database.daos.FormResponseDao
import com.cradleplatform.neptune.database.daos.HealthFacilityDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.database.views.LocalSearchPatient
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral

const val CURRENT_DATABASE_VERSION = 4

/**
 * An interface for the local CRADLE database.
 *
 * About migrations: Although we want to prioritize the data that is on the server, we don't want
 * to fall back to a destructive migration in case the user has un-uploaded [Patient]s or [Reading]s
 *
 * TODO: Lower the version back to version 1 when the app is out of alpha testing. (Refer to issues ticket #28)
 */
@Database(
    entities = [
        Reading::class,
        Patient::class,
        HealthFacility::class,
        Referral::class,
        Assessment::class,
        FormClassification::class,
        FormResponse::class
    ],
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
    abstract fun formClassificationDao(): FormClassificationDao
    abstract fun formResponseDao(): FormResponseDao

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
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
                        `id` TEXT NOT NULL, 
                        `comment` TEXT,
                        `healthFacilityName` TEXT NOT NULL, 
                        `dateReferred` INTEGER NOT NULL,
                        `userId` INTEGER,
                        `patientId` TEXT NOT NULL,
                        `actionTaken` TEXT,
                        `cancelReason` TEXT,
                        `notAttendReason` TEXT,
                        `isAssessed` INTEGER NOT NULL,
                        `isCancelled` INTEGER NOT NULL,
                        `notAttended` INTEGER NOT NULL,
                        `lastEdited` INTEGER NOT NULL,
                        `lastServerUpdate` INTEGER,
                        `isUploadedToServer` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) 
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(`healthFacilityName`) REFERENCES `HealthFacility`(`name`) 
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Assessment (
                        `id` TEXT NOT NULL, 
                        `dateAssessed` INTEGER NOT NULL,
                        `healthcareWorkerId` INTEGER NOT NULL, 
                        `patientId` TEXT NOT NULL, 
                        `diagnosis` TEXT, 
                        `treatment` TEXT, 
                        `medicationPrescribed` TEXT, 
                        `specialInvestigations` TEXT, 
                        `followUpNeeded` INTEGER,
                        `followUpInstructions` TEXT, 
                        `lastEdited` INTEGER,
                        `lastServerUpdate` INTEGER,
                        `isUploadedToServer` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Referral_id` ON `Referral` (`id`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_Referral_patientId` ON `Referral` (`patientId`)")
                execSQL(
                    """
                    CREATE INDEX 
                    IF NOT EXISTS `index_Referral_healthFacilityName` 
                    ON `Referral` (`healthFacilityName`)
                    """.trimIndent()
                )
                execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Assessment_id` ON `Assessment` (`id`)")
                execSQL("CREATE INDEX IF NOT EXISTS `index_Assessment_patientId` ON `Assessment` (`patientId`)")
            }
        }
    }

    /**
     * Version 3: add FormClassification
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS FormClassification (
                        `formClass` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `formTemplate` TEXT NOT NULL,
                        PRIMARY KEY (formClass, language)
                    )
                """.trimIndent()
            )
        }
    }

    /**
     * Version 4: add FormResponse
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.apply {
                execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS FormResponse (
                        `formResponseId` LONG NOT NULL,
                        `patientId` TEXT NOT NULL,
                        `formTemplate` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `answers` TEXT NOT NULL,
                        `saveResponseToSendLater`, BIT NOT NULL,
                        PRIMARY KEY(`formResponseId`),
                        FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) 
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_FormResponse_formResponseId`"
                        + " ON `FormResponse` (`formResponseId`)"
                )
                execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_FormResponse_patientId` "
                        + "ON `FormResponse` (`patientId`)"
                )
            }
        }
    }
}
