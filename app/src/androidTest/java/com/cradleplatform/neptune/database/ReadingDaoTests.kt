package com.cradleplatform.neptune.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.UrineTest
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ReadingDaoTests {
    
    companion object {
        private const val DATABASE = "reading-dao-test"
        private const val PATIENT_ID = "test-patient-001"
    }

    @Rule
    @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun getDatabase(): CradleDatabase {
        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CradleDatabase::class.java,
            DATABASE
        ).fallbackToDestructiveMigration().build()
        helper.closeWhenFinished(db)
        return db
    }

    @Before
    fun setUp() {
        getDatabase().apply { 
            clearAllTables(); close() 
        }
    }

        @After
    fun finish() {
        getDatabase().apply { 
            clearAllTables(); close() 
        }
    }

    private fun createPatient(): Patient = Patient(
        id = PATIENT_ID, 
        name = "Test Patient - 07", 
        dateOfBirth = "2001-05-05",
        isExactDateOfBirth = true, 
        gestationalAge = GestationalAgeWeeks(Weeks(20L)),
        sex = Sex.FEMALE, 
        isPregnant = false, 
        zone = null, 
        villageNumber = null,
        drugHistory = "", 
        medicalHistory = ""
    )


    /**
     * creates a simple reading with default values.
     * values were taken from CommonReadingsJsons.kt file to ensure they are valid and can be inserted into the database without issues
     */
    private fun createReading(
        patientId: String = PATIENT_ID,
        dateTaken: Long = 1595645893L,
        uploaded: Boolean = false
    ): Reading = Reading(
        id = UUID.randomUUID().toString(),
        patientId = patientId,
        dateTaken = dateTaken,
        lastEdited = dateTaken,
        bloodPressure = BloodPressure(110, 70, 65),
        urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
        symptoms = listOf("headache"),
        referral = Referral(
            comment = "comment", 
            healthFacilityName = "H0000", 
            dateReferred = 1595645675L,
            patientId = patientId, 
            id = UUID.randomUUID().toString(), 
            userId = 1,
            isAssessed = false, 
            actionTaken = null, 
            cancelReason = null, 
            isCancelled = false,
            lastEdited = 0L, 
            notAttendReason = null, 
            notAttended = false
        ),
        followUp = Assessment(
            id = UUID.randomUUID().toString(), 
            dateAssessed = 1595745946L,
            healthcareWorkerId = 1, 
            diagnosis = "diagnosis", 
            treatment = "treatment",
            medicationPrescribed = "medication", 
            specialInvestigations = "investigation",
            followUpNeeded = false, 
            followUpInstructions = null, 
            patientId = patientId
        ),
        dateRetestNeeded = dateTaken,
        isFlaggedForFollowUp = false,
        previousReadingIds = emptyList(),
        isUploadedToServer = uploaded,
        userId = 1
    )

    /**
     * test to verify that we can insert a reading and also retreive that reading by its id
     * and then also verifies that the retrieved reading matches the inserted reading.
     */
    @Test
    fun readingDaoInsertAndRetrieveById() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            val reading = createReading()
            db.readingDao().insert(reading)
            val retrieved = db.readingDao().getReadingById(reading.id)
            assertNotNull(retrieved)
            assertEquals(reading.id, retrieved.id)
            assertEquals(PATIENT_ID, retrieved.patientId)
        }
    }

    /**
     * test to insert patients and then verify that the number of readings are equal as well
     */
    @Test
    fun readingDaoGetAllReadingsByPatientId() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            db.readingDao().insert(createReading())
            db.readingDao().insert(createReading())
            assertEquals(2, db.readingDao().getAllReadingByPatientId(PATIENT_ID).size)
        }
    }

    /**
     * test to make sure whether the isUploaded bool correctly works and makes sure the false ones
     * are not inserted
     */
    @Test
    fun readingDaoGetAllUnUploadedReadings() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            db.readingDao().insert(createReading(uploaded = false))
            db.readingDao().insert(createReading(uploaded = true))
            assertEquals(1, db.readingDao().getAllUnUploadedReadings().size)
        }
    }

    /**
     * test to make sure if we are correctly able to mark all the readings as uploaded
     * using assert equals to test if it is enforced
     */
    @Test
    fun readingDaoMarkAllAsUploadedToServer() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            db.readingDao().insert(createReading(uploaded = false))
            db.readingDao().insert(createReading(uploaded = false))
            assertEquals(2, db.readingDao().getAllUnUploadedReadings().size)
            db.readingDao().markAllAsUploadedToServer()
            assertEquals(0, db.readingDao().getAllUnUploadedReadings().size)
        }
    }

    /**
     * test to make sure that we are able to delete all the readings from the db correctly
     */
    @Test
    fun readingDaoDeleteAllReadings() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            db.readingDao().insert(createReading())
            db.readingDao().insert(createReading())
            assertEquals(2, db.readingDao().getAllReadingEntities().size)
            db.readingDao().deleteAllReading()
            assertEquals(0, db.readingDao().getAllReadingEntities().size)
        }
    }

    /**
     * test to make sure we are able to update any existing reading.
     * using assert true to make sure this is being enforced.
     */
    @Test
    fun readingDaoUpdateReading() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient())
            val reading = createReading()
            db.readingDao().insert(reading)
            val updated = reading.copy(isFlaggedForFollowUp = true)
            db.readingDao().update(updated)
            assertTrue(db.readingDao().getReadingById(reading.id).isFlaggedForFollowUp)
        }
    }
}