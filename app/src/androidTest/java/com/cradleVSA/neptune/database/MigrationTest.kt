package com.cradleVSA.neptune.database

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.Room
import androidx.room.TypeConverter
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleVSA.neptune.ext.toList
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.BloodPressure
import com.cradleVSA.neptune.model.GestationalAge
import com.cradleVSA.neptune.model.GestationalAgeMonths
import com.cradleVSA.neptune.model.GestationalAgeWeeks
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.model.ReadingMetadata
import com.cradleVSA.neptune.model.Referral
import com.cradleVSA.neptune.model.Sex
import com.cradleVSA.neptune.model.UrineTest
import com.cradleVSA.neptune.testutils.assertEquals
import com.cradleVSA.neptune.testutils.assertForeignKeyConstraintException
import com.cradleVSA.neptune.testutils.assertThrows
import com.cradleVSA.neptune.utilitiles.DateUtil
import com.cradleVSA.neptune.utilitiles.Months
import com.cradleVSA.neptune.utilitiles.Weeks
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.util.UUID

class MigrationTests {
    private val TEST_DB = "migration-test"

    /**
     * Cannot use the normal [DatabaseTypeConverters] class; see the comments on
     * the [Version1CompatibleDatabaseTypeConverters]
     */
    private val typeConverter = Version1CompatibleDatabaseTypeConverters()

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
        val patientId = "3453455"
        val reading = createFirstVersionReading(patientId = patientId)

        val patientWithExactDob = Patient(
            id = patientId,
            name = "Exact dob",
            dob = "1989-10-24",
            isExactDob = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = "abc"
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this,
                patient = patientWithExactDob,
                ageForV1 = null
            )

            insertFirstVersionReading(
                database = this, reading = reading
            )
            // Prepare for the next version.
            close()
        }

        // Migrate to the latest version. Room handles schema validation, so we must now validate
        // that the data was migrated properly. This includes checking that all properties for
        // unaltered columns are unchanged.
        val readingDao = getMigratedRoomDatabase().readingDao()
        runBlocking {
            assertEquals(1, readingDao.getAllReadingEntities().size)
            readingDao.getReadingById(reading.id)?.let { readingFromMigratedDb ->
                assertEquals(reading, readingFromMigratedDb)
            } ?: error {
                "Missing a reading after migration. Probably ON CASCADE DELETE was invoked for " +
                    "Reading when it should not have been"
            }
        }
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
            drugHistory = "",
            medicalHistory = "Asthma"
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
            drugHistory = "drug history 2",
            medicalHistory = "drug history 2"
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
            drugHistory = "drug history 3",
            medicalHistory = "medical history 3"
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this, patient = patientWithExactDob, ageForV1 = null
            )
            insertFirstVersionReading(
                database = this, reading = createFirstVersionReading(patientWithExactDob.id)
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithApproxAgeOf23, ageForV1 = 23
            )
            insertFirstVersionReading(
                database = this, reading = createFirstVersionReading(patientWithApproxAgeOf23.id)
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithBothDobAndAgeOf19, ageForV1 = 19
            )
            insertFirstVersionReading(
                database = this,
                reading = createFirstVersionReading(patientWithBothDobAndAgeOf19.id)
            )

            // Prepare for the next version.
            close()
        }

        runBlocking {
            // Migrate to the latest version. Room handles schema validation, so we must now validate
            // that the data was migrated properly. This includes checking that all properties for
            // unaltered columns are unchanged.
            val patientDao = getMigratedRoomDatabase().patientDao()

            assertEquals(3, patientDao.getPatientIdsList().size)

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
            patientDao.getPatientById(patientWithBothDobAndAgeOf19.id)
                ?.let { patientFromMigratedDb ->
                    assertEquals(patientWithBothDobAndAgeOf19, patientFromMigratedDb)
                } ?: error("no patient")
        }
    }

    /**
     * Tests migration to the latest database version and make sure the foreign key ON DELETE
     * CASCADEs in [Reading] are not triggered during migration, and that foreign key checks are
     * on after migration.
     */
    @Test
    fun migrationTestForeignKeysAfterMigration() {
        val patientId = "3453455"
        val reading = createFirstVersionReading(patientId = patientId)
        val reading2 = createFirstVersionReading(patientId = patientId)
        val patientWithExactDob = Patient(
            id = patientId,
            name = "Exact dob",
            dob = "1989-10-24",
            isExactDob = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = "abc"
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this,
                patient = patientWithExactDob,
                ageForV1 = null
            )

            insertFirstVersionReading(
                database = this, reading = reading
            )

            insertFirstVersionReading(
                database = this, reading = reading2
            )
            // Prepare for the next version.
            close()
        }

        // Check that the foreign keys ON DELETE CASCADE didn't get triggered by dropping and
        // recreating the patient table
        val database = getMigratedRoomDatabase()
        val patientDao = database.patientDao()
        runBlocking {
            assertEquals(1, patientDao.getPatientIdsList().size) {
                "Mismatch in expected number of patients: got ${patientDao.getPatientIdsList().size}" +
                    " instead of 1"
            }

            val readingDao = database.readingDao()
            assertEquals(2, readingDao.getAllReadingEntities().size) {
                "Mismatch in expected number of patients: got " +
                    "${readingDao.getAllReadingEntities().size}" +
                    " instead of 2"
            }

            readingDao.getReadingById(reading.id)?.let { readingFromMigratedDb ->
                assertEquals(reading, readingFromMigratedDb)
            } ?: error {
                "Missing a reading after migration. Probably ON CASCADE DELETE was invoked for " +
                    "Reading when it should not have been"
            }
            readingDao.getReadingById(reading2.id)?.let { readingFromMigratedDb ->
                assertEquals(reading2, readingFromMigratedDb)
            } ?: error {
                "Missing a reading after migration. Probably ON CASCADE DELETE was invoked for " +
                    "Reading when it should not have been"
            }

            // Now, try inserting a reading for non-existent patient
            val readingForNonExistentPatient = createFirstVersionReading(patientId = "NOPE")
            val sqLiteException = assertThrows<SQLiteConstraintException> {
                readingDao.insert(readingForNonExistentPatient)
            }
            assertForeignKeyConstraintException(sqLiteException)

            // Now, try deleting a patient and assert that all the readings associated with the
            // patient are deleted
            assertEquals(1, patientDao.deleteById(patientId))
            assertEquals(0, patientDao.getPatientIdsList().size) {
                "expected no patients in the database"
            }
            assertEquals(0, readingDao.getAllReadingEntities().size) {
                "expected foreign key ON DELETE CASCADE for Readings, but it didn't happen"
            }
        }
    }

    private fun createFirstVersionReading(patientId: String): Reading {
        val unixTime: Long = 1595645893
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
        return Reading(
            id = readingId,
            patientId = patientId,
            dateTimeTaken = unixTime,
            lastEdited = unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = referralForReading,
            followUp = assessmentForReading,
            dateRecheckVitalsNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            metadata = ReadingMetadata(),
            isUploadedToServer = false,
            userId = null
        )
    }

    private fun insertFirstVersionReading(
        database: SupportSQLiteDatabase,
        readingTableName: String = "Reading",
        reading: Reading,
        // in a previous version, null was being stored as a "null" string
        urineTestForV1: String = typeConverter.fromUrineTest(reading.urineTest) ?: "null",
        referralForV1: String = typeConverter.fromReferral(reading.referral) ?: "null",
        followupForV1: String = typeConverter.fromFollowUp(reading.followUp) ?: "null",
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
                "urineTest" to urineTestForV1,
                "symptoms" to typeConverter.fromStringList(symptoms),
                "referral" to referralForV1,
                "followUp" to followupForV1,
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
        gestationalAgeForV1: String =
            typeConverter.gestationalAgeToString(patient.gestationalAge) ?: "null",
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
                "gestationalAge" to gestationalAgeForV1,
                "sex" to typeConverter.sexToString(sex),
                "isPregnant" to isPregnant,
                "zone" to zone,
                "villageNumber" to villageNumber,
                // The schema prior to version 8 has these as non-null List<String>
                "drugHistoryList" to typeConverter.fromStringList(listOf(drugHistory))!!,
                "medicalHistoryList" to typeConverter.fromStringList(listOf(medicalHistory))!!,
                "lastEdited" to lastEdited,
                "base" to base
            )
        }
        database.insert(patientsTableName, SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private fun getMigratedRoomDatabase(
        fallbackToDestructiveMigration: Boolean = false
    ): CradleDatabase {
        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            TEST_DB
        ).apply {
            if (fallbackToDestructiveMigration) {
                fallbackToDestructiveMigration()
            } else {
                addMigrations(*Migrations.ALL_MIGRATIONS)
            }
        }.build()
        // Close the database and release any stream resources when the test finishes
        helper.closeWhenFinished(database)
        return database
    }
}

/**
 * A list of [TypeConverter] to save objects into Room database for version 1.
 * This must be used in case there are any changes to the original [DatabaseTypeConverters]
 * class in the future. These are the type converters that were compatible with the
 * version 1 schema. In a sense, these are time-frozen type converters.
 */
@Suppress("unused")
private class Version1CompatibleDatabaseTypeConverters {

    fun gestationalAgeToString(gestationalAge: GestationalAge?): String? =
        gestationalAge?.let {
            JSONObject().apply {
                put("gestationalTimestamp", it.timestamp.toString())
                put(
                    "gestationalAgeUnit",
                    if (it is GestationalAgeWeeks)
                        "GESTATIONAL_AGE_UNITS_WEEKS"
                    else
                        "GESTATIONAL_AGE_UNITS_MONTHS"
                )
            }.toString()
        }

    fun stringToGestationalAge(string: String?): GestationalAge? =
        string?.let {
            if (it == "null") {
                null
            } else {
                JSONObject(it).run {
                    val units = getString("gestationalAgeUnit")
                    val value = getLong("gestationalTimestamp")
                    return when (units) {
                        "GESTATIONAL_AGE_UNIT_WEEKS" -> GestationalAgeWeeks(BigInteger.valueOf(value))
                        "GESTATIONAL_AGE_UNIT_MONTHS" -> GestationalAgeMonths(BigInteger.valueOf(value))
                        else -> throw JSONException("what")
                    }
                }
            }
        }

    fun stringToSex(string: String): Sex = enumValueOf(string)

    fun sexToString(sex: Sex): String = sex.name

    fun fromStringList(list: List<String>?): String? {
        if (list == null) {
            return null
        }
        return JSONArray()
            .apply { list.forEach { put(it) } }
            .toString()
    }

    fun toStringList(string: String?): List<String>? = string?.let {
        JSONArray(it).toList(JSONArray::getString)
    }

    fun toBloodPressure(string: String?): BloodPressure? =
        string?.let { if (it == "null") null else BloodPressure.unmarshal(JSONObject(string)) }

    fun fromBloodPressure(bloodPressure: BloodPressure?): String? =
        bloodPressure?.marshal()?.toString()

    fun toUrineTest(string: String?): UrineTest? =
        string?.let { if (it == "null") null else UrineTest.unmarshal(JSONObject(it)) }

    fun fromUrineTest(urineTest: UrineTest?): String? = urineTest?.marshal()?.toString()

    fun toReferral(string: String?): Referral? =
        string?.let { if (it == "null") null else Referral.unmarshal(JSONObject(it)) }

    fun fromReferral(referral: Referral?): String? = referral?.marshal()?.toString()

    fun toFollowUp(string: String?): Assessment? =
        string?.let { if (it == "null") null else Assessment.unmarshal(JSONObject(it)) }

    fun fromFollowUp(followUp: Assessment?): String? = followUp?.marshal()?.toString()

    fun toReadingMetadata(string: String?): ReadingMetadata? =
        string?.let { if (it == "null") null else ReadingMetadata.unmarshal(JSONObject(it)) }

    fun fromReadingMetaData(readingMetadata: ReadingMetadata?): String? =
        readingMetadata?.marshal()?.toString()
}
