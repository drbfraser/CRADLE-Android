package com.cradle.neptune.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQueryBuilder
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
    version = 3,
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
            Room.databaseBuilder(context, CradleDatabase::class.java, DATABASE_NAME)
                .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}

/**
 * Internal so that tests can access the [Migration] objects.
 * Suppress MagicNumber and NestedBlockDepth due to the migrations.
 */
@Suppress("MagicNumber", "NestedBlockDepth")
internal object Migrations {
    /**
     * Version 2:
     * Add new Reading fields (respiratoryRate, oxygenSaturation, temperature)
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
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
    val MIGRATION_2_3 = object : Migration(2, 3) {
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
}
