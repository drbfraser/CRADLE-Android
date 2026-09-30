package com.cradleplatform.neptune.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.Patient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class FormV2DraftDaoTests {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: CradleDatabase

    @Before
    fun setUp(): Unit = runBlocking {
        context.deleteDatabase(DATABASE)
        database = openDatabase()
        database.patientDao().insert(Patient(id = "patient-1", name = "First patient"))
        database.patientDao().insert(Patient(id = "patient-2", name = "Second patient"))
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun upsertReplacesOneDraftAndPreservesCreationTime() = runBlocking {
        val dao = database.formV2DraftDao()
        val original = draft()
        dao.upsert(original)
        val revised = original.copy(
            formTemplateVersion = 2,
            formTemplate = original.formTemplate.copy(version = 2, archived = true),
            answers = listOf(FormAnswerV2(questionId = "q", answer = AnswerV2.createTextAnswer("edited", "note"))),
            language = "French",
            createdAt = 2000L,
            updatedAt = 3000L
        )
        dao.upsert(revised)
        assertEquals(revised.copy(createdAt = original.createdAt), dao.getDraft("patient-1", "template-1"))
        assertEquals(1, countDrafts())
        assertNotNull(database.patientDao().getPatientById("patient-1"))

        dao.upsert(revised.copy(answers = emptyList(), updatedAt = 4000L))
        assertEquals(emptyList<FormAnswerV2>(), dao.getDraft("patient-1", "template-1")!!.answers)
    }

    @Test
    fun compositeKeySeparatesPatientsAndTemplatesAndDeleteUsesBothKeys() = runBlocking {
        val dao = database.formV2DraftDao()
        dao.upsert(draft())
        dao.upsert(draft(patientId = "patient-2"))
        dao.upsert(draft(templateId = "template-2"))
        assertEquals(3, countDrafts())
        assertEquals(1, dao.deleteDraft("patient-1", "template-1"))
        assertNull(dao.getDraft("patient-1", "template-1"))
        assertNotNull(dao.getDraft("patient-2", "template-1"))
        assertNotNull(dao.getDraft("patient-1", "template-2"))
        assertEquals(0, dao.deleteDraft("patient-1", "template-1"))
    }

    @Test
    fun concurrentUpsertsStillCreateOnlyOneRow() = runBlocking {
        val dao = database.formV2DraftDao()
        (1..10).map { value ->
            async {
                dao.upsert(draft().copy(
                    answers = listOf(FormAnswerV2(questionId = "q", answer = AnswerV2.createNumericAnswer(value)))
                ))
            }
        }.awaitAll()
        assertEquals(1, countDrafts())
    }

    @Test
    fun draftSurvivesDatabaseCloseAndReopen() = runBlocking {
        val saved = draft().copy(answers = listOf(
            FormAnswerV2(questionId = "q", answer = AnswerV2.createNumericAnswer(Long.MAX_VALUE))
        ))
        database.formV2DraftDao().upsert(saved)
        database.close()
        database = openDatabase()
        assertEquals(saved, database.formV2DraftDao().getDraft("patient-1", "template-1"))
    }

    @Test
    fun clearAllTablesRemovesDraftsAsRequiredByLogout() = runBlocking {
        database.formV2DraftDao().upsert(draft())
        database.clearAllTables()
        assertNull(database.formV2DraftDao().getDraft("patient-1", "template-1"))
    }

    @Test
    fun patientDeletionCascadesAndMissingPatientCannotHaveADraft() = runBlocking {
        database.formV2DraftDao().upsert(draft())
        database.patientDao().deleteById("patient-1")
        assertNull(database.formV2DraftDao().getDraft("patient-1", "template-1"))
        try {
            database.formV2DraftDao().upsert(draft())
            fail("Expected the patient foreign key to reject the draft")
        } catch (_: SQLiteConstraintException) {
            assertEquals(0, countDrafts())
        }
    }

    private fun openDatabase(): CradleDatabase = Room.databaseBuilder(
        context, CradleDatabase::class.java, DATABASE
    ).addMigrations(*Migrations.ALL_MIGRATIONS).build()

    private fun countDrafts(): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM FormV2Draft").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun draft(patientId: String = "patient-1", templateId: String = "template-1") = FormV2Draft(
        patientId = patientId,
        formTemplateId = templateId,
        formTemplateVersion = 1,
        formTemplate = FormTemplateV2(
            id = templateId,
            version = 1,
            classification = FormClassificationV2("classification-1", mapOf("english" to "Test"), "name-id"),
            dateCreated = 1000L,
            questions = emptyList()
        ),
        answers = emptyList(),
        createdAt = 1000L,
        updatedAt = 1000L
    )

    companion object {
        private const val DATABASE = "form-v2-draft-dao-test"
    }
}
