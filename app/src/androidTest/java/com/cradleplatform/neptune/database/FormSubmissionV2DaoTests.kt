package com.cradleplatform.neptune.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.AnswerWithQuestionV2
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.QuestionTypeEnum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FormSubmissionV2DaoTests {
    private lateinit var database: CradleDatabase

    @Before
    fun setUp(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CradleDatabase::class.java).build()
        database.patientDao().insert(Patient(id = "patient-1", name = "First patient"))
        database.patientDao().insert(Patient(id = "patient-2", name = "Second patient"))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replaceAndUpsertAffectOnlyTheRequestedPatient() = runBlocking {
        val dao = database.formSubmissionV2Dao()
        dao.upsert(submission("old", "patient-1", 100))
        val otherPatient = submission("other", "patient-2", 100)
        dao.upsert(otherPatient)
        val earlier = submission("first", "patient-1", 200)
        val later = submission("second", "patient-1", 300)
        dao.replacePatientSubmissions("patient-1", listOf(earlier, later))
        assertEquals(listOf(later, earlier), dao.observeSubmissionsByPatientId("patient-1").first())
        assertNull(dao.getSubmission("old", "patient-1"))
        assertNull(dao.getSubmission("first", "patient-2"))
        assertEquals(otherPatient, dao.getSubmission("other", "patient-2"))

        val edited = later.copy(submission = later.submission.copy(lastEdited = 400))
        dao.upsert(edited)
        assertEquals(edited, dao.getSubmission("second", "patient-1"))
        try {
            dao.replacePatientSubmissions("patient-1", listOf(otherPatient))
            fail("A different patient's records must not replace this cache")
        } catch (_: IllegalArgumentException) {
            assertEquals(listOf(edited, earlier), dao.observeSubmissionsByPatientId("patient-1").first())
        }
        dao.replacePatientSubmissions("patient-1", emptyList())
        assertTrue(dao.observeSubmissionsByPatientId("patient-1").first().isEmpty())
        assertEquals(otherPatient, dao.getSubmission("other", "patient-2"))
    }

    @Test
    fun patientDeletionAndLogoutClearCachedSubmissions() = runBlocking {
        val dao = database.formSubmissionV2Dao()
        dao.upsert(submission("first", "patient-1", 100))
        dao.upsert(submission("second", "patient-2", 200))
        database.openHelper.writableDatabase.execSQL("DELETE FROM Patient WHERE id = 'patient-1'")
        assertNull(dao.getSubmission("first", "patient-1"))
        database.clearAllTables()
        assertNull(dao.getSubmission("second", "patient-2"))
    }

    private fun submission(id: String, patientId: String, dateSubmitted: Long) = FormSubmissionV2Entity(
        submission = FormSubmissionV2(
            id = id, formTemplateId = "template-1", patientId = patientId, userId = 1,
            dateSubmitted = dateSubmitted, lastEdited = dateSubmitted, lang = "English", archived = false,
            name = "Follow-up"
        ),
        answers = listOf(AnswerWithQuestionV2(
            id = "answer-$id", questionId = "question-1", formSubmissionId = id,
            answer = AnswerV2.createTextAnswer("Headache"),
            questionType = QuestionTypeEnum.STRING, questionText = "Symptoms",
            mcOptions = emptyList(), order = 0
        ))
    )
}
