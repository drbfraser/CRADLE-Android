package com.cradle.neptune.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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
import com.cradle.neptune.testutils.assertEquals
import com.cradle.neptune.testutils.assertNotEquals
import com.cradle.neptune.testutils.assertThrows
import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
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
                dob = "1989-10-24",
                isExactDob = true,
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
                dob = "1989-10-24",
                isExactDob = false,
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
        val rowIds = patientDao.insertAll(patients)
        rowIds.forEach {
            assertNotEquals(-1, it)
        }

        val alreadyExistingPatientRowIds = patientDao.insertAll(patients)
        alreadyExistingPatientRowIds.forEach { assertEquals(-1, it) }

        val newPatient = Patient(
            id = "777",
            name = "I am a diffferent patient.",
            dob = "1989-10-24",
            isExactDob = true,
            gestationalAge = GestationalAgeWeeks(Weeks(20L)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = null,
            villageNumber = null,
            drugHistory = "",
            medicalHistory = ""
        )

        val rowIdsForMixOfExistingAndNewPatients =
            patientDao.insertAll(patients + newPatient)
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
    fun readingDao_foreignKeyConstraintInsert() {
        val database = getRoomDatabase()
        val patientDao = database.patientDao()
        val readingDao = database.readingDao()

        assertEquals(0, patientDao.getPatientIdsList().size)
        assertEquals(0, readingDao.getAllReadingEntities().size)

        // Must be prevented from inserting a Reading for a nonexistent patient.
        val reading = createReading(patientId = "1")
        val sqLiteException = assertThrows<SQLiteConstraintException> { readingDao.insert(reading) }
        assertEquals(
            "FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)",
            sqLiteException.message
        )

        // Nothing should be inserted.
        assertEquals(0, patientDao.getPatientIdsList().size)
        assertEquals(0, readingDao.getAllReadingEntities().size)
    }

    @Test
    fun readingDao_foreignKeyConstraintOnDeleteCascade() {
        val database = getRoomDatabase()
        val patientDao = database.patientDao()
        val readingDao = database.readingDao()
        assertEquals(0, patientDao.getPatientIdsList().size)
        assertEquals(0, readingDao.getAllReadingEntities().size)

        val (patient, reading) = createPatientAndReading(patientId = "1")
        database.runInTransaction {
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

    @Test
    fun patientDao_updatingDoesntDeletePreviousReadings() {
        val database = getRoomDatabase()
        val patientDao = database.patientDao()
        val readingDao = database.readingDao()
        assertEquals(0, patientDao.getPatientIdsList().size)
        assertEquals(0, readingDao.getAllReadingEntities().size)

        val (patient, reading) = createPatientAndReading(patientId = "1")
        database.runInTransaction {
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

    private fun createPatientAndReading(patientId: String): Pair<Patient, Reading> {
        val patient = Patient(
            id = patientId,
            name = "Exact dob",
            dob = "1989-10-24",
            isExactDob = true,
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
    }
}