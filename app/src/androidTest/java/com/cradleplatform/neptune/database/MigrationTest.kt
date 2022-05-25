package com.cradleplatform.neptune.database

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.database.firstversiondata.Version1TypeConverter
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.GestationalAgeMonths
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.UrineTest
import com.cradleplatform.neptune.testutils.assertEquals
import com.cradleplatform.neptune.testutils.assertForeignKeyConstraintException
import com.cradleplatform.neptune.testutils.assertThrows
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

private const val TEST_DB = "migration-test"

class MigrationTests {

    /**
     * Cannot use the normal [DatabaseTypeConverters] class; see the comments on
     * the [Version1TypeConverter]
     */
    private val v1TypeConverter = Version1TypeConverter()

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
        val reading = createFirstAndRecentVersionReading(patientId = patientId)

        val patientWithExactDob = com.cradleplatform.neptune.database.firstversiondata.model.Patient(
            id = patientId,
            name = "Exact dob",
            dob = "1989-10-24",
            isExactDob = true,
            gestationalAge = com.cradleplatform.neptune.database.firstversiondata.model.GestationalAgeWeeks(Weeks(20L)),
            sex = com.cradleplatform.neptune.database.firstversiondata.model.Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = "abc",
            allergy = ""
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this,
                patient = patientWithExactDob,
            )

            insertFirstVersionReading(
                database = this,
                reading = reading.firstVerObj,
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
            readingDao.getReadingById(reading.firstVerObj.id)?.let { readingFromMigratedDb ->
                assertEquals(reading.expectedRecentVerObj, readingFromMigratedDb)
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
        val patientWithExactDob = FirstVersionAndRecentVersion(
            com.cradleplatform.neptune.database.firstversiondata.model.Patient(
                id = "1",
                name = "Exact dob",
                dob = "1989-10-24",
                isExactDob = true,
                gestationalAge = com.cradleplatform.neptune.database.firstversiondata.model.GestationalAgeWeeks(Weeks(20L)),
                sex = com.cradleplatform.neptune.database.firstversiondata.model.Sex.FEMALE,
                isPregnant = true,
                zone = null,
                villageNumber = null,
                drugHistory = "",
                medicalHistory = "Asthma",
                allergy = ""
            ),
            Patient(
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
                medicalHistory = "Asthma",
                allergy = ""
            )
        )
        val patientWithApproxAgeOf23 = FirstVersionAndRecentVersion(
            com.cradleplatform.neptune.database.firstversiondata.model.Patient(
                id = "2",
                name = "Approximate age of 23",
                dob = null,
                isExactDob = false,
                gestationalAge = com.cradleplatform.neptune.database.firstversiondata.model.GestationalAgeMonths(Months(4)),
                sex = com.cradleplatform.neptune.database.firstversiondata.model.Sex.OTHER,
                isPregnant = true,
                zone = "zone2",
                villageNumber = "villageNumber2",
                drugHistory = "drug history 2",
                medicalHistory = "drug history 2",
                allergy = ""
            ),
            Patient(
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
                medicalHistory = "drug history 2",
                allergy = ""
            )
        )
        val patientWithBothDobAndAgeOf19 = FirstVersionAndRecentVersion(
            com.cradleplatform.neptune.database.firstversiondata.model.Patient(
                id = "3",
                name = "Has both age and dob -- prefer dob",
                dob = "1954-04-24",
                isExactDob = true,
                gestationalAge = null,
                sex = com.cradleplatform.neptune.database.firstversiondata.model.Sex.MALE,
                isPregnant = false,
                zone = "zone3",
                villageNumber = "villageNumber3",
                drugHistory = "drug history 3",
                medicalHistory = "medical history 3",
                allergy = ""
            ),
            Patient(
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
                medicalHistory = "medical history 3",
                allergy = ""
            )
        )
        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this, patient = patientWithExactDob.firstVerObj
            )
            insertFirstVersionReading(
                database = this,
                reading = createFirstAndRecentVersionReading(patientWithExactDob.firstVerObj.id).firstVerObj
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithApproxAgeOf23.firstVerObj
            )
            insertFirstVersionReading(
                database = this,
                reading = createFirstAndRecentVersionReading(patientWithApproxAgeOf23.firstVerObj.id).firstVerObj
            )

            insertFirstVersionPatient(
                database = this, patient = patientWithBothDobAndAgeOf19.firstVerObj
            )
            insertFirstVersionReading(
                database = this,
                reading = createFirstAndRecentVersionReading(patientWithBothDobAndAgeOf19.firstVerObj.id).firstVerObj
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
            patientDao.getPatientById(patientWithExactDob.firstVerObj.id)
                ?.let { patientFromMigratedDb ->
                    assertEquals(
                        patientWithExactDob.expectedRecentVerObj,
                        patientFromMigratedDb
                    )
                } ?: error("no patient")

            // Approximate age should have isExactDob false
            // Age is non-null but dob is null, so the dob should be not exact.
            patientDao.getPatientById(patientWithApproxAgeOf23.firstVerObj.id)
                ?.let { patientFromMigratedDb ->
                    assertEquals(
                        patientWithApproxAgeOf23.expectedRecentVerObj,
                        patientFromMigratedDb
                    )
                } ?: error("no patient")

            // Assume exact if there is somehow both age and dob
            patientDao.getPatientById(patientWithBothDobAndAgeOf19.firstVerObj.id)
                ?.let { patientFromMigratedDb ->
                    assertEquals(
                        patientWithBothDobAndAgeOf19.expectedRecentVerObj,
                        patientFromMigratedDb
                    )
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
        val reading = createFirstAndRecentVersionReading(patientId = patientId)
        val reading2 = createFirstAndRecentVersionReading(patientId = patientId)
        val patientWithExactDob = FirstVersionAndRecentVersion(
            com.cradleplatform.neptune.database.firstversiondata.model.Patient(
                id = patientId,
                name = "Exact dob",
                dob = "1989-10-24",
                isExactDob = true,
                gestationalAge = com.cradleplatform.neptune.database.firstversiondata.model.GestationalAgeWeeks(Weeks(20L)),
                sex = com.cradleplatform.neptune.database.firstversiondata.model.Sex.FEMALE,
                isPregnant = true,
                zone = null,
                villageNumber = null,
                drugHistory = "",
                medicalHistory = "abc",
                allergy = ""
            ),
            Patient(
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
                medicalHistory = "abc",
                allergy = ""
            )
        )

        helper.createDatabase(TEST_DB, 1).apply {
            // db has schema version 1. Need to insert some data using SQL queries.
            // Can't use DAO classes; they expect the latest schema.
            insertFirstVersionPatient(
                database = this,
                patient = patientWithExactDob.firstVerObj,
            )

            insertFirstVersionReading(
                database = this, reading = reading.firstVerObj
            )

            insertFirstVersionReading(
                database = this, reading = reading2.firstVerObj
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

            readingDao.getReadingById(reading.firstVerObj.id)
                ?.let { readingFromMigratedDb ->
                    assertEquals(reading.expectedRecentVerObj, readingFromMigratedDb)
                }
                ?: error {
                    "Missing a reading after migration. Probably ON CASCADE DELETE was invoked for " +
                        "Reading when it should not have been"
                }
            readingDao.getReadingById(reading2.firstVerObj.id)
                ?.let { readingFromMigratedDb ->
                assertEquals(reading2.expectedRecentVerObj, readingFromMigratedDb)
                }
                ?: error {
                    "Missing a reading after migration. Probably ON CASCADE DELETE was invoked for " +
                        "Reading when it should not have been"
                }

            // Now, try inserting a reading for non-existent patient
            val readingForNonExistentPatient = createCurrentVersionReading(patientId = "NOPE")
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

    private fun createFirstAndRecentVersionReading(
        patientId: String
    ) : FirstVersionAndRecentVersion<com.cradleplatform.neptune.database.firstversiondata.model.Reading, Reading> {
        val unixTime: Long = 1595645893
        val readingId = UUID.randomUUID().toString()
        val firstVersionReferral = com.cradleplatform.neptune.database.firstversiondata.model.Referral(
            comment = "This is a comment",
            healthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            readingId = readingId,
            id = 345,
            userId = 2,
            isAssessed = true
        )
        val recentVersionReferral = Referral(
            comment = "This is a comment",
            referralHealthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            id = "345",
            userId = 2,
            isAssessed = true,
            actionTaken = null,
            cancelReason = null,
            isCancelled = false,
            lastEdited = 0L,
            notAttendReason = null,
            notAttended = false
        )
        val firstVersionAssessment = com.cradleplatform.neptune.database.firstversiondata.model.Assessment(
            id = 4535,
            dateAssessed = 1595745946L,
            healthCareWorkerId = 2,
            readingId = readingId,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followupNeeded = true,
            followupInstructions = "These are things to do"
        )
        val recentVersionAssessment = Assessment(
            id = "4535",
            dateAssessed = 1595745946L,
            healthCareWorkerId = 2,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followupNeeded = true,
            followupInstructions = "These are things to do",
            patientId = patientId
        )

        return FirstVersionAndRecentVersion(
            com.cradleplatform.neptune.database.firstversiondata.model.Reading(
                id = readingId,
                patientId = patientId,
                dateTimeTaken = unixTime,
                lastEdited = unixTime,
                bloodPressure = com.cradleplatform.neptune.database.firstversiondata.model.BloodPressure(110, 70, 65),
                urineTest = com.cradleplatform.neptune.database.firstversiondata.model.UrineTest("+", "++", "NAD", "NAD", "NAD"),
                symptoms = listOf("headache", "blurred vision", "pain"),
                referral = firstVersionReferral,
                followUp = firstVersionAssessment,
                dateRecheckVitalsNeeded = unixTime,
                isFlaggedForFollowUp = true,
                previousReadingIds = listOf("1", "2", "3"),
                isUploadedToServer = false,
                userId = null
            ),
            Reading(
                id = readingId,
                patientId = patientId,
                dateTimeTaken = unixTime,
                lastEdited = unixTime,
                bloodPressure = BloodPressure(110, 70, 65),
                urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
                symptoms = listOf("headache", "blurred vision", "pain"),
                referral = recentVersionReferral,
                followUp = recentVersionAssessment,
                dateRecheckVitalsNeeded = unixTime,
                isFlaggedForFollowUp = true,
                previousReadingIds = listOf("1", "2", "3"),
                isUploadedToServer = false,
                userId = null
            )
        )
    }

    private fun createCurrentVersionReading(patientId: String): Reading {
        val unixTime: Long = 1595645893
        val readingId = UUID.randomUUID().toString()
        val referralForReading = Referral(
            comment = "This is a comment",
            referralHealthFacilityName = "H2230",
            dateReferred = 1595645675L,
            patientId = patientId,
            id = "345",
            userId = 2,
            isAssessed = true,
            actionTaken = null,
            cancelReason = null,
            isCancelled = false,
            lastEdited = 0L,
            notAttendReason = null,
            notAttended = false
        )
        val assessmentForReading = Assessment(
            id = "4535",
            dateAssessed = 1595745946L,
            healthCareWorkerId = 2,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followupNeeded = true, followupInstructions = "These are things to do",
            patientId = patientId
        )

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
            isUploadedToServer = false,
            userId = null
        )
    }

    private fun insertFirstVersionReading(
        database: SupportSQLiteDatabase,
        readingTableName: String = "Reading",
        reading: com.cradleplatform.neptune.database.firstversiondata.model.Reading,
    ) {
        val values = reading.run {
            // Using string literals, because we need to use the property names for the v1 schema.
            // We don't do statements like Reading::id.name, since that can get affected by
            // refactoring names.
            // @PrimaryKey
            //     @ColumnInfo(name = "readingId")
            //     var id: String = UUID.randomUUID().toString(),
            //     @ColumnInfo var patientId: String,
            //     @ColumnInfo var dateTimeTaken: Long,
            //     @ColumnInfo var bloodPressure: BloodPressure,
            //     @ColumnInfo var urineTest: UrineTest?,
            //     @ColumnInfo var symptoms: List<String>,
            //     @ColumnInfo var referral: Referral?,
            //     @ColumnInfo var followUp: Assessment?,
            //     @ColumnInfo var dateRecheckVitalsNeeded: Long?,
            //     @ColumnInfo var isFlaggedForFollowUp: Boolean,
            //     @ColumnInfo var previousReadingIds: List<String> = emptyList(),
            //     @ColumnInfo var isUploadedToServer: Boolean = false,
            //     @ColumnInfo var lastEdited: Long,
            //     @ColumnInfo var userId: Int?
            contentValuesOf(
                "readingId" to id,
                "patientId" to patientId,
                "dateTimeTaken" to dateTimeTaken,
                "bloodPressure" to v1TypeConverter.fromBloodPressure(bloodPressure),
                "urineTest" to v1TypeConverter.fromUrineTest(urineTest),
                "symptoms" to v1TypeConverter.fromStringList(symptoms),
                "referral" to v1TypeConverter.fromReferral(referral),
                "followUp" to v1TypeConverter.fromFollowUp(followUp),
                "dateRecheckVitalsNeeded" to dateRecheckVitalsNeeded,
                "isFlaggedForFollowUp" to isFlaggedForFollowUp,
                "previousReadingIds" to v1TypeConverter.fromStringList(previousReadingIds),
                "isUploadedToServer" to isUploadedToServer,
                "lastEdited" to lastEdited,
                "userId" to userId
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
        patient: com.cradleplatform.neptune.database.firstversiondata.model.Patient
    ) {
        val values = patient.run {
            //     var id: String = "",
            //     @ColumnInfo var name: String = "",
            //     @ColumnInfo var dob: String? = null,
            //     @ColumnInfo var isExactDob: Boolean? = null,
            //     @ColumnInfo var gestationalAge: GestationalAge? = null,
            //     @ColumnInfo var sex: Sex = Sex.OTHER,
            //     @ColumnInfo var isPregnant: Boolean = false,
            //     @ColumnInfo var zone: String? = null,
            //     @ColumnInfo var villageNumber: String? = null,
            //     @ColumnInfo var householdNumber: String? = null,
            //     @ColumnInfo var drugHistory: String = "",
            //     @ColumnInfo var medicalHistory: String = "",
            //     @ColumnInfo var allergy: String = "",
            //     @ColumnInfo var lastEdited: Long? = null,
            //     @ColumnInfo var lastServerUpdate: Long? = null
            contentValuesOf(
                "id" to id,
                "name" to name,
                "dob" to dob,
                "isExactDob" to isExactDob,
                "gestationalAge" to v1TypeConverter.gestationalAgeToString(gestationalAge),
                "sex" to v1TypeConverter.sexToString(sex),
                "isPregnant" to isPregnant,
                "zone" to zone,
                "villageNumber" to villageNumber,
                "householdNumber" to householdNumber,
                "drugHistory" to drugHistory,
                "medicalHistory" to medicalHistory,
                "allergy" to allergy,
                "lastEdited" to lastEdited,
                "lastServerUpdate" to lastServerUpdate
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
