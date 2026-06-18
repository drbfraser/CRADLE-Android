package com.cradleplatform.neptune.database

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.BloodPressure
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.model.UrineTest
import com.cradleplatform.neptune.testutils.assertForeignKeyConstraintException
import com.cradleplatform.neptune.testutils.assertThrows
import com.cradleplatform.neptune.utilities.Weeks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID



class DatabaseIntegrityTests {
    companion object {
        private const val DATABASE = "database-integrity-test"
        private const val FORM_CLASS_ID = "test-form-class-001"
        private const val FORM_TEMPLATE_ID = "test-form-template-001"
    }

    @Rule
    @JvmField
    val helper : MigrationTestHelper = MigrationTestHelper(
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
        getDatabase().apply { clearAllTables(); close() }
    }

    @After
    fun finish() {
        getDatabase().apply { clearAllTables(); close() }
    }

    private fun createPatient(id: String): Patient = Patient(
        id = id, name = "Patient $id", 
        dateOfBirth = "1990-01-01", 
        isExactDateOfBirth = true,
        gestationalAge = GestationalAgeWeeks(Weeks(20L)), 
        sex = Sex.FEMALE,
        isPregnant = false, 
        zone = null, 
        villageNumber = null, 
        drugHistory = "", 
        medicalHistory = ""
    )

    private fun createReading(patientId: String): Reading = Reading(
        id = UUID.randomUUID().toString(), 
        patientId = patientId,
        dateTaken = 1595645893L, 
        lastEdited = 1595645893L,
        bloodPressure = BloodPressure(110, 70, 65),
        urineTest = UrineTest("+", "++", "NAD", "NAD", "NAD"),
        symptoms = listOf("headache"),
        referral = Referral(
            comment = "comment", healthFacilityName = "H0000", dateReferred = 1595645675L,
            patientId = patientId, id = UUID.randomUUID().toString(), userId = 1,
            isAssessed = false, actionTaken = null, cancelReason = null, isCancelled = false,
            lastEdited = 0L, notAttendReason = null, notAttended = false
        ),
        followUp = Assessment(
            id = UUID.randomUUID().toString(), dateAssessed = 1595745946L,
            healthcareWorkerId = 1, diagnosis = "diagnosis", treatment = "treatment",
            medicationPrescribed = "medication", specialInvestigations = "investigation",
            followUpNeeded = false, followUpInstructions = null, patientId = patientId
        ),
        dateRetestNeeded = 1595645893L, 
        isFlaggedForFollowUp = false,
        previousReadingIds = emptyList(), 
        isUploadedToServer = false, userId = 1
    )

    private fun createFormResponse(patientId: String): FormResponse = FormResponse(
        patientId = patientId,
        formTemplate = FormTemplate(
            version = "1.0", 
            archived = false, 
            dateCreated = 1000000L,
            id = FORM_TEMPLATE_ID, 
            formClassId = FORM_CLASS_ID, 
            formClassName = "Test Form",
            questions = listOf(
                Question(
                    id = "q1", 
                    allowPastDates = null, 
                    allowFutureDates = null,
                    visibleCondition = emptyList(), 
                    isBlank = true, 
                    formTemplateId = FORM_TEMPLATE_ID,
                    questionIndex = 0, 
                    numMin = null, 
                    numMax = null, stringMaxLength = null,
                    stringMaxLines = null, 
                    questionType = QuestionTypeEnum.STRING,
                    hasCommentAttached = false, 
                    required = false,
                    languageVersions = listOf(
                        QuestionLangVersion("english", "q1", "Test question", 1, emptyList())
                    )
                )
            )
        ),
        language = "english",
        answers = mapOf("q1" to Answer.createTextAnswer("test answer")),
        saveResponseToSendLater = false
    )

    @Test
    fun integrityReadingRequiresExistingPatient() {
        val db = getDatabase()
        val exception = assertThrows<SQLiteConstraintException> {
            runBlocking { 
                db.readingDao().insert(createReading("nonexistent-patient")) 
            }
        }
        assertForeignKeyConstraintException(exception)
    }

    @Test
    fun integrityFormResponseRequiresExistingPatient() {
        val db = getDatabase()
        val exception = assertThrows<SQLiteConstraintException> {
            runBlocking { 
                db.formResponseDao().insert(createFormResponse("nonexistent-patient")) 
            }
        }
        assertForeignKeyConstraintException(exception)
    }

    @Test
    fun integrityDeletePatientCascadesToReadings() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient("p1"))
            db.readingDao().insert(createReading("p1"))
            assertEquals(1, db.readingDao().getAllReadingEntities().size)
            db.patientDao().deleteById("p1")
            assertEquals(0, db.readingDao().getAllReadingEntities().size)
        }
    }

    @Test
    fun integrityDeletePatientCascadesToFormResponses() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient("p1"))
            db.formResponseDao().insert(createFormResponse("p1"))
            assertEquals(1, db.formResponseDao().getAllFormResponses().size)
            db.patientDao().deleteById("p1")
            assertEquals(0, db.formResponseDao().getAllFormResponses().size)
        }
    }

    @Test
    fun integrityMultiplePatientsDontShareData() {
        runBlocking {
            val db = getDatabase()
            db.patientDao().insert(createPatient("p1"))
            db.patientDao().insert(createPatient("p2"))
            db.readingDao().insert(createReading("p1"))
            db.readingDao().insert(createReading("p2"))
            db.formResponseDao().insert(createFormResponse("p1"))

            assertEquals(1, db.readingDao().getAllReadingByPatientId("p1").size)
            assertEquals(1, db.readingDao().getAllReadingByPatientId("p2").size)
            assertEquals(1, db.formResponseDao().getAllFormResponseByPatientId("p1")?.size)
            assertEquals(0, db.formResponseDao().getAllFormResponseByPatientId("p2")?.size ?: 0)
        }
    }

}