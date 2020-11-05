package com.cradle.neptune.database

import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.cradle.neptune.database.Migrations.MIGRATION_1_2
import com.cradle.neptune.database.Migrations.MIGRATION_2_3
import com.cradle.neptune.database.Migrations.MIGRATION_3_4
import com.cradle.neptune.model.Assessment
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAgeMonths
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import com.cradle.neptune.utilitiles.DateUtil
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MigrationTests {
    private val TEST_DB = "migration-test"

    private val typeConverter = DatabaseTypeConverters()

    @Rule @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Tests migration to the latest database version.
     *
     * Adapted from:
     * * https://developer.android.com/training/data-storage/room/
     *   migrating-db-versions#single-migration-test
     *
     * * https://github.com/android/architecture-components-samples/blob/master/
     *   PersistenceMigrationsSample/app/src/androidTestRoom3/java/com/example/android/persistence/
     *   migrations/MigrationTest.java
     */
    @Test
    fun migrateReadingTableFromVersion1ToLatest() {
        val unixTime: Long = 1595645893
        val patientId = "5414842504"
        val readingId = UUID.randomUUID().toString()
        val referralForReading = Referral(
            comment = "This is a comment",
            healthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            readingId = readingId,
            id = 345,
            userId = 2,
            isAssessed = true
        )
        val assessmentForReading = Assessment(
            id = 4535,
            dateAssessed = 1595745946L,
            healthCareWorkerId = 2,
            readingId = readingId,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followupNeeded = true, followupInstructions = "These are things to do"
        )

        // respiratoryRate, oxygenSaturation, temperature are required to be null, because they
        // weren't present in the v1 schema.
        val reading = Reading(
            id = readingId,
            patientId = patientId,
            dateTimeTaken = unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            respiratoryRate = null,
            oxygenSaturation = null,
            temperature = null,
            urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = referralForReading,
            followUp = assessmentForReading,
            dateRecheckVitalsNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            metadata = ReadingMetadata(),
            isUploadedToServer = false
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionReading(
                database = this, reading = reading
            )
            // Prepare for the next version.
            close()
        }

        // Re-open the database with the latest version, and provide the migration processes.
        helper.runMigrationsAndValidate(
            TEST_DB, 4, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        // MigrationTestHelper automatically verifies the schema changes,
        // Validate that the data was migrated properly. This includes checking that all properties
        // for unaltered columns are unchanged.
        val readingDao = getMigratedRoomDatabase().readingDao()
        readingDao.getReadingById(reading.id)?.let { readingFromMigratedDb ->
            assertEquals(reading, readingFromMigratedDb)
        } ?: error("no reading")
    }

    /**
     * Tests migrations for the Patient table to the latest database version.
     *
     * Adapted from:
     * * https://developer.android.com/training/data-storage/room/
     *   migrating-db-versions#single-migration-test
     *
     * * https://github.com/android/architecture-components-samples/blob/master/
     *   PersistenceMigrationsSample/app/src/androidTestRoom3/java/com/example/android/persistence/
     *   migrations/MigrationTest.java
     */
    @Test
    fun migratePatientTableFromVersion1ToLatest() {
        val patientWithExactDob = Patient(
            id = "1",
            name = "Exact dob",
            dob = "1989-10-24",
            isExactDob = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistoryList = emptyList(),
            medicalHistoryList = emptyList()
        )
        val patientWithApproxAgeOf23 = Patient(
            id = "2",
            name = "Approximate age of 23",
            dob = null,
            isExactDob = false,
            gestationalAge = GestationalAgeMonths(Months(4)),
            sex = Sex.OTHER,
            isPregnant = true,
            zone = "zone2",
            villageNumber = "villageNumber2",
            drugHistoryList = listOf("drug history 2"),
            medicalHistoryList = listOf("drug history 2")
        )
        val patientWithBothDobAndAgeOf19 = Patient(
            id = "3",
            name = "Has both age and dob -- prefer dob",
            dob = "1954-04-24",
            isExactDob = true,
            gestationalAge = null,
            sex = Sex.MALE,
            isPregnant = false,
            zone = "zone3",
            villageNumber = "villageNumber3",
            drugHistoryList = listOf("drug history 3"),
            medicalHistoryList = listOf("medical history 3")
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this, patient = patientWithExactDob, ageForV1 = null
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithApproxAgeOf23, ageForV1 = 23
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithBothDobAndAgeOf19, ageForV1 = 19
            )

            // Prepare for the next version.
            close()
        }

        // Re-open the database with the latest version, and provide the migration processes.
        helper.runMigrationsAndValidate(
            TEST_DB, 4, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        // MigrationTestHelper automatically verifies the schema changes,
        // Validate that the data was migrated properly. This includes checking that all unchanged
        // properties remain, and that the isExactDob is correctly set from the available
        // information.
        val patientDao = getMigratedRoomDatabase().patientDao()

        // Test that the isExactDob is set depending on the nullity of dob and age.
        // patientWithExactDob has a non-null dob, so the dob should be exact.
        patientDao.getPatientById(patientWithExactDob.id)?.let { patientFromMigratedDb ->
            assertEquals(patientWithExactDob, patientFromMigratedDb)
        } ?: error("no patient")

        // Approximate age should have isExactDob false
        // Age is non-null but dob is null, so the dob should be not exact.
        patientDao.getPatientById(patientWithApproxAgeOf23.id)?.let { patientFromMigratedDb ->
            patientWithApproxAgeOf23.dob = DateUtil.getDateStringFromAge(23)
            assertEquals(patientWithApproxAgeOf23, patientFromMigratedDb)
        } ?: error("no patient")

        // Assume exact if there is somehow both age and dob
        patientDao.getPatientById(patientWithBothDobAndAgeOf19.id)?.let { patientFromMigratedDb ->
            assertEquals(patientWithBothDobAndAgeOf19, patientFromMigratedDb)
        } ?: error("no patient")
    }

    private fun insertFirstVersionReading(
        database: SupportSQLiteDatabase,
        readingTableName: String = "Reading",
        reading: Reading
    ) {
        val values = reading.run {
            // Using string literals, because we need to use the property names for the v1 schema.
            // We don't do statements like Reading::id.name, since that can get affected by
            // refactoring names.
            contentValuesOf(
                "readingId" to id,
                "patientId" to patientId,
                "dateTimeTaken" to dateTimeTaken,
                "bloodPressure" to typeConverter.fromBloodPressure(bloodPressure),
                "urineTest" to typeConverter.fromUrineTest(urineTest),
                "symptoms" to typeConverter.fromStringList(symptoms),
                "referral" to typeConverter.fromReferral(referral),
                "followUp" to typeConverter.fromFollowUp(followUp),
                "dateRecheckVitalsNeeded" to dateRecheckVitalsNeeded,
                "isFlaggedForFollowUp" to isFlaggedForFollowUp,
                "previousReadingIds" to typeConverter.fromStringList(previousReadingIds),
                "metadata" to typeConverter.fromReadingMetaData(metadata),
                "isUploadedToServer" to isUploadedToServer
            )
        }
        database.insert(readingTableName, SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    /**
     * Inserts a Patient for the version 1 schema into the given [database].
     * Given a [patient] from the current schema version, it will extract the values of the
     * properties that haven't changed since version 1.
     *
     * [ageForV1] is required as a parameter, because it was removed in version 3 but age is
     * available in the version 1 schema.
     */
    private fun insertFirstVersionPatient(
        database: SupportSQLiteDatabase,
        patientsTableName: String = "Patient",
        patient: Patient,
        ageForV1: Int?,
    ) {
        val values = patient.run {
            // Using string literals, because we need to use the property names for the v1 schema.
            // We don't do statements like Patient::id.name, since that can get affected by
            // refactoring names.
            contentValuesOf(
                "id" to id,
                "name" to name,
                "dob" to dob,
                "age" to ageForV1,
                "gestationalAge" to typeConverter.gestationalAgeToString(gestationalAge),
                "sex" to typeConverter.sexToString(sex),
                "isPregnant" to isPregnant,
                "zone" to zone,
                "villageNumber" to villageNumber,
                "drugHistoryList" to typeConverter.fromStringList(drugHistoryList),
                "medicalHistoryList" to typeConverter.fromStringList(medicalHistoryList),
                "lastEdited" to lastEdited,
                "base" to base
            )
        }
        database.insert(patientsTableName, SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private fun getMigratedRoomDatabase(): CradleDatabase {
        val database: CradleDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            TEST_DB
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        // Close the database and release any stream resources when the test finishes
        helper.closeWhenFinished(database)
        return database
    }
}