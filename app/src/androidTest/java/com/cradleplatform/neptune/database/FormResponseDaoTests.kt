package com.cradleplatform.neptune.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.McOption
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import org.junit.After
import org.junit.Before
import org.junit.Rule

/** tests for all the FormResponseDao methods
 * instrument tests to be run on an emulated device on Firebase Test Lab
 */

class FormResponseDaoTests {

    companion object{
        private const val DATABASE = "form-response-dao-test"
        private const val PATIENT_ID = "test-patient-001"
        private const val FORM_TEMPLATE_ID = "test-form-template-001"
        private const val FORM_CLASS_ID = "test-form-class-001"
    }

    @Rule
    @JvmField
    val helper : MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CradleDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

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
    fun setUp(){
        getDatabase().apply {
            clearAllTables()
            close()
        }        
    }

    @After
    fun finish(){
        getDatabase().apply {
            clearAllTables()
            close()
        } 
    }

    private fun createTestPatient(): Patient = Patient(
        id = PATIENT_ID,
        name = "Test Patient",
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

    private fun createTestFormTemplate(): FormTemplate {
        val textQuestion = Question(
            id = "q1",
            allowPastDates = null,
            allowFutureDates = null,
            visibleCondition = emptyList(),
            isBlank = true,
            formTemplateId = FORM_TEMPLATE_ID,
            questionIndex = 0,
            numMin = null,
            numMax = null,
            stringMaxLength = null,
            stringMaxLines = null,
            questionType = QuestionTypeEnum.STRING,
            hasCommentAttached = false,
            required = false,
            languageVersions = listOf(
                QuestionLangVersion(
                    language = "english",
                    parentId = "q1",
                    questionText = "Patient name",
                    questionTextId = 1,
                    mcOptions = emptyList()
                )
            )
        )

    val mcQuestion = Question(
            id = "q2",
            allowPastDates = null,
            allowFutureDates = null,
            visibleCondition = emptyList(),
            isBlank = true,
            formTemplateId = FORM_TEMPLATE_ID,
            questionIndex = 1,
            numMin = null,
            numMax = null,
            stringMaxLength = null,
            stringMaxLines = null,
            questionType = QuestionTypeEnum.MULTIPLE_CHOICE,
            hasCommentAttached = false,
            required = false,
            languageVersions = listOf(
                QuestionLangVersion(
                    language = "english",
                    parentId = "q2",
                    questionText = "Select symptom",
                    questionTextId = 2,
                    mcOptions = listOf(
                        McOption(mcId = 0, opt = "Headache"),
                        McOption(mcId = 1, opt = "Fever"),
                        McOption(mcId = 2, opt = "Nausea")
                    )
                )
            )
        )

        return FormTemplate(
            version = "1.0",
            archived = false,
            dateCreated = 1000000L,
            id = FORM_TEMPLATE_ID,
            formClassId = FORM_CLASS_ID,
            formClassName = "Test Form",
            questions = listOf(textQuestion, mcQuestion)
        )
    }
}