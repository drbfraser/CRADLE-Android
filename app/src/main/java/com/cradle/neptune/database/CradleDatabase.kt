package com.cradle.neptune.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import com.cradle.neptune.database.daos.HealthFacilityDao
import com.cradle.neptune.database.daos.PatientDao
import com.cradle.neptune.database.daos.ReadingDao
import com.cradle.neptune.database.views.LocalSearchPatient
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.DateUtil

/**
 * An interface for the local CRADLE database.
 *
 * About migrations: Although we want to prioritize the data that is on the server, we don't want
 * to fall back to a destructive migration in case the user has un-uploaded [Patient]s or [Reading]s
 *
 * TODO: Lower the version back to version 1 when the app is out of alpha testing.
 */
@Database(
    entities = [Reading::class, Patient::class, HealthFacility::class],
    views = [LocalSearchPatient::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(DatabaseTypeConverters::class)
abstract class CradleDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun patientDao(): PatientDao
    abstract fun healthFacility(): HealthFacilityDao

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
    val ALL_MIGRATIONS by lazy {
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8
        )
    }

    /**
     * Version 2:
     * Add new Reading fields (respiratoryRate, oxygenSaturation, temperature)
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.run {
                execSQL("ALTER TABLE Reading ADD COLUMN respiratoryRate INTEGER")
                execSQL("ALTER TABLE Reading ADD COLUMN oxygenSaturation INTEGER")
                execSQL("ALTER TABLE Reading ADD COLUMN temperature INTEGER")
            }
        }
    }

    /**
     * Version 3:
     * Add new Patient properties (isExactDob and householdNumber) and remove age property.
     * Removing the age property requires us to migrate the age and dob states to the new isExactDob
     * Boolean.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE Patient ADD COLUMN isExactDob INTEGER")
            val dobAgeQuery = SupportSQLiteQueryBuilder.builder("Patient")
                .columns(arrayOf("id", "dob", "age"))
                .create()
            database.query(dobAgeQuery).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val dob = cursor.getStringOrNull(cursor.getColumnIndexOrThrow("dob"))
                    val age = cursor.getIntOrNull(cursor.getColumnIndexOrThrow("age"))

                    // Update the isExactDob boolean based on the age state.
                    val updateValues = ContentValues().apply {
                        when {
                            dob != null && age == null -> {
                                // If we already had a date of birth and no age, we will assume it's
                                // exact.
                                put("isExactDob", true)
                            }
                            dob != null && age != null -> {
                                // If we some how have both an age and date of birth, we prefer
                                // date of birth. The age will be ignored later on when we drop the
                                // age column from the Patient table.
                                put("isExactDob", true)
                            }
                            dob == null && age != null -> {
                                // Calculate an approximate date of birth from the age for now. This
                                // approximate date of birth expected to be overwritten by the
                                // server later.
                                put("isExactDob", false)
                                put("dob", DateUtil.getDateStringFromAge(age.toLong()))
                            }
                            else -> {
                                // Here, both dob and age are null. For now, just set isExactDob to
                                // false.
                                put("isExactDob", false)
                            }
                        }
                    }
                    database.update(
                        "Patient" /* table */,
                        SQLiteDatabase.CONFLICT_REPLACE /* conflictAlgorithm */,
                        updateValues /* values */,
                        "id = ?" /* whereClause */,
                        arrayOf(id) /* whereArgs */
                    )
                }
            }

            // Finally, drop the age column from the table.
            // Because SQLite doesn't support dropping columns, we have to now recreate the table
            // with the age column removed.
            //
            // It's easy to do this in terms of writing code. Just go to where the schemas are
            // exported ($projectDir/app/schemas/com.cradle.neptune.Database.CradleDatabase/3.json)
            // and paste the createSql statement from version 3 here.
            // Use `vim` with :set tw=75 or so to set the text width, and have it wrap the text for
            // you with gqq (or gqip).
            database.run {
                execSQL(
                    """
                    CREATE TABLE new_Patient (
                        `id` TEXT NOT NULL, `name` TEXT NOT NULL, `dob` TEXT, `isExactDob` INTEGER,
                        `gestationalAge` TEXT, `sex` TEXT NOT NULL, `isPregnant` INTEGER NOT NULL,
                        `zone` TEXT, `villageNumber` TEXT, `householdNumber` TEXT, `drugHistoryList`
                        TEXT NOT NULL, `medicalHistoryList` TEXT NOT NULL, `lastEdited` INTEGER,
                        `base` INTEGER, PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                val properties = "`id`,`name`,`dob`,`isExactDob`,`gestationalAge`,`sex`," +
                    "`isPregnant`,`zone`,`villageNumber`,`drugHistoryList`,`medicalHistoryList`," +
                    "`lastEdited`,`base`"
                execSQL("INSERT INTO new_Patient ($properties) SELECT $properties FROM Patient")
                execSQL("DROP TABLE Patient")
                execSQL("ALTER TABLE new_Patient RENAME TO Patient")
            }
        }
    }

    /**
     * Version 4:
     * Stop using Gson for DatabaseTypeConverters and switch to the marshalling we use.
     * Also, switch the UrineTests to use NAD instead of -.
     *
     * This means that we have to change all the BloodPressure and UrineTest JSONs to use the field
     * name, as it was using Gson for serialization before. Using Gson meant that the stored String
     * in the database used the property names for BloodPressure and UrineTest instead of the
     * BloodPressureField and UrineTestField names.
     *
     * There are other field and property name mismatches that we have to correct here.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // For whatever reason, Gson stored null instances in the database as Strings that
            // just say "null". This takes up space, so we need to manually change those to a null
            // that SQLite recognizes.
            //
            // For the Patient table, some old versions might still be storing "null" String for no
            // gestational age.
            database.apply {
                execSQL("UPDATE Reading SET urineTest = null WHERE urineTest = 'null'")
                execSQL("UPDATE Reading SET referral = null WHERE referral = 'null'")
                execSQL("UPDATE Reading SET followUp = null WHERE followUp = 'null'")
                execSQL("UPDATE Patient SET gestationalAge = null WHERE gestationalAge = 'null'")
            }

            val dobAgeQuery = SupportSQLiteQueryBuilder.builder("Reading")
                .columns(arrayOf("readingId", "bloodPressure", "urineTest", "followUp", "referral"))
                .create()
            database.query(dobAgeQuery).use { cursor ->
                while (cursor != null && cursor.moveToNext()) {
                    val readingId = cursor.getString(
                        cursor.getColumnIndexOrThrow("readingId")
                    )

                    val updatedValues = ContentValues().apply {
                        val bloodPressure = cursor.getString(
                            cursor.getColumnIndexOrThrow("bloodPressure")
                        )

                        // Since we're now using the BloodPressure fields to store, we need to
                        // convert the JSONObjects of the form
                        // {"diastolic":90,"heartRate":91,"systolic":114} into
                        // {"bpDiastolic":90,"heartRateBPM":91,"bpSystolic":114}
                        val newBloodPressure = bloodPressure
                            .replace("\"diastolic\"", "\"bpDiastolic\"")
                            .replace("\"systolic\"", "\"bpSystolic\"")
                            .replace("\"heartRate\"", "\"heartRateBPM\"")
                        put("bloodPressure", newBloodPressure)

                        // Use the field names
                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow("urineTest")
                        )?.let { urineTestString ->
                            val newUrineTestString = urineTestString
                                // Use NAD instead of -
                                .replace("\"-\"", "\"NAD\"")
                                .replace("\"leukocytes\"", "\"urineTestLeuc\"")
                                .replace("\"nitrites\"", "\"urineTestNit\"")
                                .replace("\"protein\"", "\"urineTestPro\"")
                                .replace("\"blood\"", "\"urineTestBlood\"")
                                .replace("\"glucose\"", "\"urineTestGlu\"")
                            put("urineTest", newUrineTestString)
                        }

                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow("followUp")
                        )?.let { followUpString ->
                            // Servers uses different names for these fields
                            val newFollowUpString = followUpString
                                .replace("\"healthCareWorkerId\"", "\"healthcareWorkerId\"")
                            put("followUp", newFollowUpString)
                        }

                        cursor.getStringOrNull(
                            cursor.getColumnIndexOrThrow("referral")
                        )?.let { referralString ->
                            // Servers uses different names for these fields
                            val newReferralString = referralString
                                .replace("\"healthFacilityName\"", "\"referralHealthFacilityName\"")
                            put("referral", newReferralString)
                        }
                    }

                    database.update(
                        "Reading" /* table */,
                        SQLiteDatabase.CONFLICT_REPLACE /* conflictAlgorithm */,
                        updatedValues /* values */,
                        "readingId = ?" /* whereClause */,
                        arrayOf(readingId) /* whereArgs */
                    )
                }
            }
        }
    }

    /**
     * Version 5:
     * Add LocalSearchPatient view
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
CREATE VIEW `LocalSearchPatient` AS SELECT
  p.name,
  p.id,
  p.villageNumber,
  r.bloodPressure as latestBloodPressure,
  MAX(r.dateTimeTaken) as latestReadingDate
FROM
  Patient as p
  LEFT JOIN Reading AS r ON p.id = r.patientId
GROUP BY 
  r.patientId
                """.trimIndent()
            )
        }
    }

    /**
     * Version 6:
     * Update LocalSearchPatient view to include referrals
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP VIEW `LocalSearchPatient`")
            database.execSQL(
                """
CREATE VIEW `LocalSearchPatient` AS SELECT
  p.name,
  p.id,
  p.villageNumber,
  r.bloodPressure as latestBloodPressure,
  MAX(r.dateTimeTaken) as latestReadingDate,
  r.referral
FROM
  Patient as p
  LEFT JOIN Reading AS r ON p.id = r.patientId
GROUP BY 
  r.patientId
                """.trimIndent()
            )
        }
    }

    /**
     * Version 7:
     * Add indices for Patient and Reading, set a foreign key in Reading
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.apply {
                execSQL(
                    """
CREATE TABLE IF NOT EXISTS `new_Patient` (
`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dob` TEXT, `isExactDob` INTEGER, `gestationalAge` TEXT, 
`sex` TEXT NOT NULL, `isPregnant` INTEGER NOT NULL, `zone` TEXT, `villageNumber` TEXT, 
`householdNumber` TEXT, `drugHistoryList` TEXT NOT NULL, `medicalHistoryList` TEXT NOT NULL, 
`lastEdited` INTEGER, `base` INTEGER, PRIMARY KEY(`id`)
)
                    """.trimIndent()
                )
                execSQL("CREATE UNIQUE INDEX `index_Patient_id` ON `new_Patient` (`id`)")
                val patientProperties = "`id`,`name`,`dob`,`isExactDob`,`gestationalAge`," +
                    "`sex`,`isPregnant`,`zone`,`villageNumber`,`householdNumber`," +
                    "`drugHistoryList`,`medicalHistoryList`,`lastEdited`,`base`"
                execSQL(
                    "INSERT INTO new_Patient ($patientProperties) " +
                        "SELECT $patientProperties FROM Patient"
                )
                execSQL("DROP TABLE Patient")
                execSQL("ALTER TABLE new_Patient RENAME TO Patient")

                execSQL(
                    """
CREATE TABLE IF NOT EXISTS `new_Reading` (
`readingId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `dateTimeTaken` INTEGER NOT NULL, 
`bloodPressure` TEXT NOT NULL, `respiratoryRate` INTEGER, `oxygenSaturation` INTEGER, 
`temperature` INTEGER, `urineTest` TEXT, `symptoms` TEXT NOT NULL, `referral` TEXT, 
`followUp` TEXT, `dateRecheckVitalsNeeded` INTEGER, `isFlaggedForFollowUp` INTEGER NOT NULL, 
`previousReadingIds` TEXT NOT NULL, `metadata` TEXT NOT NULL, 
`isUploadedToServer` INTEGER NOT NULL, 
PRIMARY KEY(`readingId`), 
FOREIGN KEY(`patientId`) REFERENCES `Patient`(`id`) ON UPDATE CASCADE ON DELETE CASCADE 
)
                    """.trimIndent()
                )
                execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_Reading_readingId` " +
                        "ON `new_Reading` (`readingId`)"
                )
                execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_Reading_patientId` " +
                        "ON `new_Reading` (`patientId`)"
                )
                val readingProperties = "`readingId`,`patientId`,`dateTimeTaken`," +
                    "`bloodPressure`,`respiratoryRate`,`oxygenSaturation`," +
                    "`temperature`,`urineTest`,`symptoms`,`referral`,`followUp`," +
                    "`dateRecheckVitalsNeeded`,`isFlaggedForFollowUp`," +
                    "`previousReadingIds`,`metadata`,`isUploadedToServer`"
                execSQL(
                    "INSERT INTO new_Reading ($readingProperties) " +
                        "SELECT $readingProperties FROM Reading"
                )
                execSQL("DROP TABLE Reading")
                execSQL("ALTER TABLE new_Reading RENAME TO Reading")
            }
        }
    }

    /**
     * Version 8:
     * Change drug and medical history in [Patient] to just be strings instead of List<String>.
     * It's a paragraph entry, so doesn't make sense to assume it's a list.
     * Also, the column names were changed
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.apply {
                execSQL(
                    "ALTER TABLE `Patient` RENAME COLUMN `drugHistoryList` to `drugHistory`"
                )
                execSQL(
                    "ALTER TABLE `Patient` RENAME COLUMN `medicalHistoryList` to `medicalHistory`"
                )

                // Now, we have to convert all of the lists in the database to just strings
                val medicalDrugHistoryQuery = SupportSQLiteQueryBuilder.builder("Patient")
                    .columns(arrayOf("id", "drugHistory", "medicalHistory"))
                    .create()

                val typeConverter = DatabaseTypeConverters()
                query(medicalDrugHistoryQuery).use { cursor ->
                    while (cursor != null && cursor.moveToNext()) {
                        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        val drugHistoryAsList = cursor
                            .getString(cursor.getColumnIndexOrThrow("drugHistory"))
                        val medicalHistoryAsList = cursor
                            .getString(cursor.getColumnIndexOrThrow("medicalHistory"))

                        // It's a non-null column
                        val drugHistory = typeConverter.toStringList(drugHistoryAsList)!!
                            .joinToString(",")
                        val medicalHistory = typeConverter.toStringList(medicalHistoryAsList)!!
                            .joinToString(",")

                        val updateValues = contentValuesOf(
                            "drugHistory" to drugHistory,
                            "medicalHistory" to medicalHistory
                        )

                        database.update(
                            "Patient" /* table */,
                            SQLiteDatabase.CONFLICT_REPLACE /* conflictAlgorithm */,
                            updateValues /* values */,
                            "id = ?" /* whereClause */,
                            arrayOf(id) /* whereArgs */
                        )
                    }
                }
            }
        }
    }
}
