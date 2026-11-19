package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.daos.FormSubmissionV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FormSubmissionV2ManagerTest {
    private val restApi = mockk<RestApi>()
    private val dao = mockk<FormSubmissionV2Dao>()
    private val templates = mockk<FormTemplateV2Manager>()
    private val manager = FormSubmissionV2Manager(restApi, dao, templates)
    private val summary = FormSubmissionV2("s1", "t1", "p1", 1, 100, 110, "English", false, "Form")
    private val detail = FormSubmissionWithAnswersV2("s1", "t1", "p1", 1, 100, 110, "English", false, emptyList())

    @Test
    fun `accepted submission caches payload and template without another HTTP request`() = runTest {
        val question = mockk<FormTemplateQuestionV2>(relaxed = true)
        every { question.id } returns "q1"
        every { question.questionType } returns QuestionTypeEnum.STRING
        every { question.questionText } returns mapOf("english" to "Symptoms")
        every { question.mcOptions } returns emptyList()
        val template = FormTemplateV2(
            id = "t1", version = 1, dateCreated = 1,
            classification = FormClassificationV2("c1", mapOf("english" to "Form"), null),
            questions = listOf(question)
        )
        val request = CreateFormSubmissionRequestV2(
            id = "s1", formTemplateId = "t1", patientId = "p1", lang = "English",
            answers = listOf(FormAnswerV2(questionId = "q1", answer = AnswerV2.createTextAnswer("Headache")))
        )
        coEvery { templates.cacheTemplate(template) } returns Unit
        coEvery { dao.upsert(any()) } returns Unit
        manager.cacheSubmittedForm(summary, request, template)
        coVerify { dao.upsert(match {
            it.submission == summary && it.answers.single().answer.textAnswer == "Headache" &&
                it.answers.single().questionText == "Symptoms"
        }) }
        coVerify { templates.cacheTemplate(template) }
        coVerify(exactly = 0) { restApi.getFormSubmissionV2(any()) }
        coVerify(exactly = 0) { restApi.getPatientFormSubmissionsV2(any()) }
    }

    @Test
    fun `accepted POST is cached while sync waits and stale sync cannot remove it`() = runTest {
        val downloading = CompletableDeferred<Unit>()
        val response = CompletableDeferred<NetworkResult<List<FormSubmissionV2>>>()
        coEvery { restApi.getPatientFormSubmissionsV2("p1") } coAnswers {
            downloading.complete(Unit)
            response.await()
        }
        val template = FormTemplateV2(
            id = "t1", version = 1, dateCreated = 1,
            classification = FormClassificationV2("c1", mapOf("english" to "Form"), null)
        )
        val request = CreateFormSubmissionRequestV2(
            id = "s1", formTemplateId = "t1", patientId = "p1", answers = emptyList()
        )
        coEvery { templates.cacheTemplate(template) } returns Unit
        coEvery { dao.upsert(any()) } returns Unit
        coEvery { dao.replacePatientSubmissions(any(), any()) } returns Unit
        val sync = async { manager.syncPatientSubmissions("p1") }
        downloading.await()

        manager.cacheSubmittedForm(summary, request, template)
        coVerify(exactly = 1) { dao.upsert(any()) }
        assertTrue(!response.isCompleted)
        response.complete(NetworkResult.Success(emptyList(), 200))
        assertTrue(sync.await() is NetworkResult.Success)
        coVerify(exactly = 0) { dao.replacePatientSubmissions(any(), any()) }

        assertTrue(manager.syncPatientSubmissions("p1") is NetworkResult.Success)
        coVerify(exactly = 1) { dao.replacePatientSubmissions("p1", emptyList()) }
    }

    @Test
    fun `sync replaces records only after answers and template are available`() = runTest {
        coEvery { restApi.getPatientFormSubmissionsV2("p1") } returns NetworkResult.Success(listOf(summary), 200)
        coEvery { restApi.getFormSubmissionV2("s1") } returns NetworkResult.Success(detail, 200)
        coEvery { templates.getTemplate("t1") } returns NetworkResult.Success(mockk<FormTemplateV2>(), 200)
        coEvery { dao.replacePatientSubmissions("p1", any()) } returns Unit

        assertTrue(manager.syncPatientSubmissions("p1") is NetworkResult.Success)
        coVerify { dao.replacePatientSubmissions("p1", listOf(FormSubmissionV2Entity(summary, emptyList()))) }
    }

    @Test
    fun `failed detail or template download preserves previous records`() = runTest {
        coEvery { restApi.getPatientFormSubmissionsV2("p1") } returns NetworkResult.Success(listOf(summary), 200)
        coEvery { restApi.getFormSubmissionV2("s1") } returns NetworkResult.Failure(ByteArray(0), 503)
        assertTrue(manager.syncPatientSubmissions("p1") is NetworkResult.Failure)

        coEvery { restApi.getFormSubmissionV2("s1") } returns NetworkResult.Success(detail, 200)
        coEvery { templates.getTemplate("t1") } returns NetworkResult.Failure(ByteArray(0), 503)
        assertTrue(manager.syncPatientSubmissions("p1") is NetworkResult.Failure)
        coVerify(exactly = 0) { dao.replacePatientSubmissions(any(), any()) }
    }

    @Test
    fun `cached detail is available without HTTP`() = runTest {
        coEvery { dao.getSubmission("s1", "p1") } returns FormSubmissionV2Entity(summary, emptyList())
        val result = manager.getSubmission("s1", "p1") as NetworkResult.Success
        assertEquals(detail, result.value)
        coVerify(exactly = 0) { restApi.getFormSubmissionV2(any()) }
        coVerify(exactly = 0) { restApi.getPatientFormSubmissionsV2(any()) }
    }

    @Test
    fun `wrong patient detail does not replace cache`() = runTest {
        coEvery { restApi.getPatientFormSubmissionsV2("p1") } returns NetworkResult.Success(listOf(summary), 200)
        coEvery { restApi.getFormSubmissionV2("s1") } returns NetworkResult.Success(detail.copy(patientId = "p2"), 200)
        assertTrue(manager.syncPatientSubmissions("p1") is NetworkResult.NetworkException)
        coVerify(exactly = 0) { dao.replacePatientSubmissions(any(), any()) }
    }
}
