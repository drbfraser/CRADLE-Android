package com.cradleplatform.neptune.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.withTransaction
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.database.daos.PatientDao
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
import com.cradleplatform.neptune.testutils.assertNotEquals
import com.cradleplatform.neptune.testutils.assertThrows
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Tests the database and its DAO objects.
 * This is an instrumented test, and has to be run on a physical or emulated device.
 *
 * These tests are for the current database version.
 */
class DaoTests {
    companion object {
        private const val DATABASE = "database-test"
    }

    @Rule
    @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun getRoomDatabase(): CradleDatabase {
        val database: CradleDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            DATABASE
        ).fallbackToDestructiveMigration().build()
        // Close the database and release any stream resources when the test finishes
        helper.closeWhenFinished(database)
        return database
    }

    @Before
    fun before() {
        getRoomDatabase().apply {
            clearAllTables()
            close()
        }
    }

    @After
    fun after() {
        getRoomDatabase().apply {
            clearAllTables()
            close()
        }
    }

    @Test
    fun testPatientDao_insertAndInsertAll() {
        val database = getRoomDatabase()

        val patients = listOf(
            Patient(
                id = "1",
                name = "Exact dob",
                dateOfBirth = "1989-10-24",
                isExactDateOfBirth = true,
                gestationalAge = GestationalAgeWeeks(Weeks(20L)),
                sex = Sex.FEMALE,
                isPregnant = true,
                zone = null,
                villageNumber = null,
                drugHistory = "",
                medicalHistory = ""
            ),
            Patient(
                id = "2",
                name = "Not exact dob",
                dateOfBirth = "1989-10-24",
                isExactDateOfBirth = false,
                gestationalAge = GestationalAgeMonths(Months(10L)),
                sex = Sex.FEMALE,
                isPregnant = true,
                zone = "45",
                villageNumber = "2333",
                drugHistory = "",
                medicalHistory = ""
            ),
        )

        val patientDao = database.patientDao()
        val rowIds = runBlocking { patientDao.insertAll(patients) }
        rowIds.forEach {
            assertNotEquals(-1, it)
        }

        val alreadyExistingPatientRowIds = runBlocking { patientDao.insertAll(patients) }
        alreadyExistingPatientRowIds.forEach { assertEquals(-1, it) }

        val newPatient = Patient(
            id = "777",
            name = "I am a diffferent patient.",
            dateOfBirth = "1989-10-24",
            isExactDateOfBirth = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = ""
        )

        val rowIdsForMixOfExistingAndNewPatients = runBlocking {
             patientDao.insertAll(patients + newPatient)
        }
        rowIdsForMixOfExistingAndNewPatients.forEachIndexed { index, rowId ->
            // The newPatient appended to the end should not have -1 returned for its rowId
            if (index != rowIdsForMixOfExistingAndNewPatients.size - 1) {
                assertEquals(-1, rowId)
            } else {
                assertNotEquals(-1, rowId)
            }
        }
    }

    @Test
    fun testPatientDao_updateOrInsertIfNotExists() {
        val database = getRoomDatabase()

        val originalPatient = Patient(
            id = "115",
            name = "Exact dob",
            dateOfBirth = "1989-10-24",
            isExactDateOfBirth = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = "",
            lastServerUpdate = null
        )
        val patientDao = database.patientDao()
        runBlocking { patientDao.updateOrInsertIfNotExists(originalPatient) }
        val firstPatientFromDb = runBlocking { patientDao.getPatientById(originalPatient.id) }
        assertEquals(originalPatient, firstPatientFromDb)

        val updatedPatient = Patient(
            id = "115",
            name = "Exact dober",
            dateOfBirth = "1989-10-24",
            isExactDateOfBirth = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = "",
            lastServerUpdate = null
        )
        runBlocking { patientDao.updateOrInsertIfNotExists(updatedPatient) }

        val patientFromDb = runBlocking { patientDao.getPatientById(updatedPatient.id) }
        assert(updatedPatient == patientFromDb) {
            "\nexpected: $originalPatient\n" +
                "     got: $patientFromDb"
        }
    }

    @Test
    fun readingDao_foreignKeyConstraintInsert() {
        val database = getRoomDatabase()
        val patientDao = database.patientDao()
        val readingDao = database.readingDao()

        assertEquals(0, runBlocking { patientDao.getPatientIdsList() }.size)
        assertEquals(0, runBlocking { readingDao.getAllReadingEntities() }.size)

        // Must be prevented from inserting a Reading for a nonexistent patient.
        val reading = createReading(patientId = "1")

        val sqLiteException = assertThrows<SQLiteConstraintException> {
            runBlocking { readingDao.insert(reading) }
        }

        assertForeignKeyConstraintException(sqLiteException)

        // Nothing should be inserted.
        assertEquals(0, runBlocking { patientDao.getPatientIdsList().size })
        assertEquals(0, runBlocking { readingDao.getAllReadingEntities().size })
    }

    @Test
    fun readingDao_foreignKeyConstraintOnDeleteCascade() {
        runBlocking {
            val database = getRoomDatabase()
            val patientDao = database.patientDao()
            val readingDao = database.readingDao()
            assertEquals(0, patientDao.getPatientIdsList().size)
            assertEquals(0, readingDao.getAllReadingEntities().size)

            val (patient, reading) = createPatientAndReading(patientId = "1")
            database.withTransaction {
                patientDao.insert(patient)
                readingDao.insert(reading)
            }
            assertEquals(patient, patientDao.getPatientById("1"))
            assertEquals(listOf(reading), readingDao.getAllReadingEntities())

            // Delete the patient, and the reading should also be deleted via the foreign key ON DELETE
            patientDao.deleteById("1")
            assertEquals(null, patientDao.getPatientById("1"))
            assertEquals(0, patientDao.getPatientIdsList().size)
            assertEquals(emptyList<Reading>(), readingDao.getAllReadingEntities())
        }
    }

    @Test
    fun patientDao_updatingDoesntDeletePreviousReadings() {
        runBlocking {
            val database = getRoomDatabase()
            val patientDao = database.patientDao()
            val readingDao = database.readingDao()
            assertEquals(0, patientDao.getPatientIdsList().size)
            assertEquals(0, readingDao.getAllReadingEntities().size)

            val (patient, reading) = createPatientAndReading(patientId = "1")
            database.withTransaction {
                patientDao.insert(patient)
                readingDao.insert(reading)
            }
            assertEquals(patient, patientDao.getPatientById("1"))
            assertEquals(listOf(reading), readingDao.getAllReadingEntities())
            assertEquals(1, patientDao.getPatientIdsList().size)
            assertEquals(1, readingDao.getAllReadingEntities().size)

            // Running insert again does not affect the patient and the reading.
            // -1 is returend by the PatientDao.insert function to indicate that
            // the insertion did nothing.
            assertEquals(-1, patientDao.insert(patient))
            readingDao.insert(reading)
            // Check that everything else didn't change.
            assertEquals(patient, patientDao.getPatientById("1"))
            assertEquals(listOf(reading), readingDao.getAllReadingEntities())
            assertEquals(1, patientDao.getPatientIdsList().size)
            assertEquals(1, readingDao.getAllReadingEntities().size)

            // Update should change it
            val editedPatient = patient.copy().apply { householdNumber += " 111" }
            assertEquals(1, patientDao.update(editedPatient)) {
                "expected an update to happen"
            }
            val insertedEditedPatient = patientDao.getPatientById("1") ?: error("missing")
            assertEquals(editedPatient, insertedEditedPatient) { "edit didn't make it" }
            assertNotEquals(patient, insertedEditedPatient) {
                "expected the patients to be different, but they both were the same"
            }
            // Check that the Reading wasn't deleted by the foreign key ON CASCADE
            assertEquals(listOf(reading), readingDao.getAllReadingEntities())
            assertEquals(1, patientDao.getPatientIdsList().size)
            assertEquals(1, readingDao.getAllReadingEntities().size)

            // updateOrInsertIfNotExists should change it too
            val editedPatient2 = editedPatient.copy().apply { name += " the third" }
            patientDao.updateOrInsertIfNotExists(editedPatient2)

            val secondEditedPatientFromDb = patientDao.getPatientById("1") ?: error("missing")
            assertEquals(editedPatient2, secondEditedPatientFromDb) {
                "edit didn't make it. Expected name of ${editedPatient.name}, but the patient" +
                    "from the database has name ${secondEditedPatientFromDb.name}"
            }
            assertNotEquals(editedPatient, secondEditedPatientFromDb) {
                "expected the patients to be different, but they both were the same"
            }
            // Check that the Reading wasn't deleted by the foreign key ON CASCADE
            assertEquals(listOf(reading), readingDao.getAllReadingEntities())
            assertEquals(1, patientDao.getPatientIdsList().size)
            assertEquals(1, readingDao.getAllReadingEntities().size)
        }
    }

    /**
     * Creates two different patients and tests [PatientDao.getPatientAndReadingsById].
     * It makes sure it only returns readings for the patient and that it returns all of them.
     */
    @Test
    fun patientDao_getPatientAndAllAssociatedReadings() {
        runBlocking {
            val database = getRoomDatabase()
            val patientDao = database.patientDao()
            val readingDao = database.readingDao()
            assertEquals(0, patientDao.getPatientIdsList().size)
            assertEquals(0, readingDao.getAllReadingEntities().size)

            val (patientA, readingForA) = createPatientAndReading(patientId = "1")
            val anotherReadingForA = createReading(patientId = patientA.id)
            database.withTransaction {
                patientDao.insert(patientA)
                readingDao.insert(readingForA)
                readingDao.insert(anotherReadingForA)
            }
            assertEquals(patientA, patientDao.getPatientById(patientA.id))
            assertEquals(listOf(readingForA, anotherReadingForA), readingDao.getAllReadingEntities())
            assertEquals(1, patientDao.getPatientIdsList().size)

            val (patientB, readingForB) = createPatientAndReading(patientId = "2")
            val anotherReadingForB = createReading(patientId = patientB.id)
            database.withTransaction {
                patientDao.insert(patientB)
                readingDao.insert(readingForB)
                readingDao.insert(anotherReadingForB)
            }
            assertEquals(patientB, patientDao.getPatientById(patientB.id))
            assertEquals(listOf(readingForB, anotherReadingForB), readingDao.getAllReadingByPatientId(patientB.id))
            assertEquals(2, patientDao.getPatientIdsList().size)
            assertEquals(
                listOf(readingForA, anotherReadingForA, readingForB, anotherReadingForB),
                readingDao.getAllReadingEntities()
            )

            val patientAWithReadings = patientDao.getPatientAndReadingsById(patientA.id)
                ?: error("missing patient A")
            assertEquals(patientA, patientAWithReadings.patient)
            assertEquals(listOf(readingForA, anotherReadingForA), patientAWithReadings.readings)

            val patientBWithReadings = patientDao.getPatientAndReadingsById(patientB.id)
                ?: error("missing patient B")
            assertEquals(patientB, patientBWithReadings.patient)
            assertEquals(listOf(readingForB, anotherReadingForB), patientBWithReadings.readings)
        }
    }

    private fun createPatientAndReading(patientId: String): Pair<Patient, Reading> {
        val patient = Patient(
            id = patientId,
            name = "Exact dob",
            dateOfBirth = "1989-10-24",
            isExactDateOfBirth = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "Drug hsitroy",
            medicalHistory = "Asthma"
        )
        val reading = createReading(patientId)
        return patient to reading
    }

    private fun createReading(patientId: String): Reading {
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
            healthcareWorkerId = 2,
            diagnosis = "This is a detailed diagnosis.",
            treatment = "This is a treatment",
            medicationPrescribed = "These are medications prescripted.",
            specialInvestigations = "This is a special investiation",
            followUpNeeded = true, followUpInstructions = "These are things to do",
            patientId = patientId
        )

        // respiratoryRate, oxygenSaturation, temperature are required to be null, because they
        // weren't present in the v1 schema.
        return Reading(
            id = readingId,
            patientId = patientId,
            dateTaken = unixTime,
            lastEdited = unixTime,
            bloodPressure = BloodPressure(110, 70, 65),
            urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
            symptoms = listOf("headache", "blurred vision", "pain"),
            referral = referralForReading,
            followUp = assessmentForReading,
            dateRetestNeeded = unixTime,
            isFlaggedForFollowUp = true,
            previousReadingIds = listOf("1", "2", "3"),
            isUploadedToServer = false,
            userId = 1
        )
    }
}