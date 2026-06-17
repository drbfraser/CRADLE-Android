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

}