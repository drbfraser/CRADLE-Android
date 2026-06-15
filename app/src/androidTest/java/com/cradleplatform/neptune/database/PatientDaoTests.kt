package com.cradleplatform.neptune.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PatientDaoTests {

    companion object{
        private const val DATABASE = "patient-dao-test"
        private const val PATIENT_ID = "test-patient-001"
    }

    @Rule
    @JvmField
    val helper : MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * helper function to get us a reference of the database to run our tests on
     * using migration test helper to ensure db is cleared after each test run
     */
    private fun getDatabase() : CradleDatabase{
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
        getDatabase().apply { clearAllTables(); close() }
    }

    @After
    fun finish() {
        getDatabase().apply { clearAllTables(); close() }
    }

    private fun createTestPatient(id: String = PATIENT_ID, name: String = "Test Patient") = Patient(
        id = id,
        name = name,
        dateOfBirth = "2004-05-05",
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
     * test to verify that we can insert a patient and also retreive that patient by their id
     * and then also verifies that the retrieved patient matches the inserted patient.
     * also using runBlocking to run the test in a coroutine scope since the dao functions are suspend functions
     */
    @Test
    fun patientDaoInsertAndRetrieveByPatientId(){
        runBlocking {
            val db = getDatabase()
            val patient = createTestPatient()
            db.patientDao().insert(patient)
            val retrievedPatient = db.patientDao().getPatientById(PATIENT_ID)
            assertNotNull(retrievedPatient)
            assertEquals(patient.id, retrievedPatient!!.id)
            assertEquals(patient.name, retrievedPatient!!.name)
            assertEquals(patient.dateOfBirth, retrievedPatient!!.dateOfBirth)
            assertEquals(patient.isExactDateOfBirth, retrievedPatient!!.isExactDateOfBirth)
            assertEquals(patient.gestationalAge, retrievedPatient!!.gestationalAge)
            assertEquals(patient.sex, retrievedPatient!!.sex)
            assertEquals(patient.isPregnant, retrievedPatient!!.isPregnant)
            assertEquals(patient.zone, retrievedPatient!!.zone)
            assertEquals(patient.villageNumber, retrievedPatient!!.villageNumber)
            assertEquals(patient.drugHistory, retrievedPatient!!.drugHistory)
            assertEquals(patient.medicalHistory, retrievedPatient!!.medicalHistory)
        }
    }

    /**
     * test to verify that inserting a patient with an already existing id returns -1
     * and that the first insert returns a valid row id (not -1)
     */
    @Test
    fun patientDaoInsertReturnsNegative1ForDuplicatePatient(){
        runBlocking {
            val db = getDatabase()
            val patient = createTestPatient()
            val firstInsert = db.patientDao().insert(patient)
            val secondInsert = db.patientDao().insert(patient)
            assertNotEquals(-1L, firstInsert)
            assertEquals(-1L, secondInsert)
        }
    }

    /**
     * test to verify that we can insert multiple patients and then retrieve a list of
     * all the patient ids. also verifies that the number of patient ids retrieved matches 
     * the number of patients inserted
     */
    @Test
    fun patientDaoInsertAll(){
        runBlocking {
            val db = getDatabase()
            val patients = listOf(createTestPatient("t1", "Test Patient 1"), createTestPatient("t2", "Test Patient 2"), createTestPatient("t3", "Test Patient 3"))
            val rowIds = db.patientDao().insertAll(patients)
            assertEquals(3, rowIds.size)
            assertEquals(3, db.patientDao().getPatientIdsList().size)
        }       
    }

    /**
     * test to verify if we are correctly able to update a patient in the db and then fetch that patient to verify db
     * updates are working as expected. also verifies that the updated patient has the new values and not the old values.
     */
    @Test
    fun patientDaoUpdatePatient(){
        runBlocking {
            val db = getDatabase()
            val patient = createTestPatient()
            db.patientDao().insert(patient)
            val updatedPatient = patient.copy().apply { name = "updated name for test"; dateOfBirth = "1999-05-05" }
            db.patientDao().update(updatedPatient)

            val retrievedPatient = db.patientDao().getPatientById(PATIENT_ID)
            assertNotNull(retrievedPatient)
            assertEquals("updated name for test", retrievedPatient!!.name)
            assertEquals("1999-05-05", retrievedPatient.dateOfBirth)
            assertNotEquals(patient.name, retrievedPatient.name)
            assertNotEquals(patient.dateOfBirth, retrievedPatient.dateOfBirth)
        }
    }

    /**
     * test that deletes the patient by id and then tests that it can no longer be retrieved by its id 
     */
    @Test
    fun patientDaoDeletePatientByID(){
        runBlocking {
            val db = getDatabase()
            val patient = createTestPatient()
            db.patientDao().insert(patient)
            db.patientDao().deleteById(PATIENT_ID)
            assertNull(db.patientDao().getPatientById(PATIENT_ID))
        }
    }

    /**
     * test that deletes all the patients in the db and then tests if the list of patient ids is empty after the delete operation
     * also tests that after inserting multiple patients and then deleting all patients, the list of patient ids is empty
     */
    @Test
    fun patientDaoDeleteAllPatientsInDb(){
        runBlocking {
            val db = getDatabase()
            val patients = listOf(createTestPatient("t1", "Test Patient 1"), createTestPatient("t2", "Test Patient 2"), createTestPatient("t3", "Test Patient 3"))
            db.patientDao().insertAll(patients)
            db.patientDao().deleteAllPatients()
            assertTrue(db.patientDao().getPatientIdsList().isEmpty())
        }
    }

    /**
     * test that inserts a patient, then tests that it can be retrieved by its id and makes sure it is accurate
     */
    @Test
    fun patientDaoUpdateOrInsertIfNotExists(){
        runBlocking {
            val db = getDatabase()
            val patient = createTestPatient()
            db.patientDao().updateOrInsertIfNotExists(patient)
            val retrievedPatient = db.patientDao().getPatientById(PATIENT_ID)
            assertNotNull(retrievedPatient)
            assertEquals(patient.id, retrievedPatient!!.id)
            assertEquals(patient.name, retrievedPatient!!.name)
        }
    }

}
