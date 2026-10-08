package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.QuestionTypeEnum
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class FormV2RenderingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var restApi: RestApi
    private lateinit var draftDao: FormV2DraftDao

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        restApi = mockk()
        draftDao = mockk()
        coEvery { restApi.getFormTemplateV2("template-1") } returns NetworkResult.Success(template(), 200)
        coEvery { draftDao.getDraft("patient-1", "template-1") } returns null
        coEvery { draftDao.upsert(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save reports success only after the write completes and captures answers at the request`() =
        runTest(testDispatcher) {
            val writeCompleted = CompletableDeferred<Unit>()
            var savedDraft: FormV2Draft? = null
            coEvery { draftDao.upsert(any()) } coAnswers {
                savedDraft = firstArg()
                writeCompleted.await()
            }
            val viewModel = createViewModel()
            runCurrent()
            viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

            viewModel.saveDraft()
            viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Fever"))
            runCurrent()

            assertEquals(FormV2DraftSaveState.Saving, viewModel.draftSaveState.value)
            assertEquals("Headache", savedDraft?.answers?.single()?.answer?.textAnswer)
            assertEquals("patient-1", savedDraft?.patientId)
            assertEquals(template(), savedDraft?.formTemplate)
            writeCompleted.complete(Unit)
            runCurrent()
            assertEquals(FormV2DraftSaveState.Saved, viewModel.draftSaveState.value)
            assertEquals("Fever", viewModel.answerState.getAnswer("q1")?.textAnswer)
        }

    @Test
    fun `save failure retains answers and allows retry`() = runTest(testDispatcher) {
        coEvery { draftDao.upsert(any()) } throws IllegalStateException("Database unavailable")
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.saveDraft()
        runCurrent()
        assertEquals(FormV2DraftSaveState.Error, viewModel.draftSaveState.value)
        assertEquals("Headache", viewModel.answerState.getAnswer("q1")?.textAnswer)

        coEvery { draftDao.upsert(any()) } returns Unit
        viewModel.saveDraft()
        runCurrent()
        assertEquals(FormV2DraftSaveState.Saved, viewModel.draftSaveState.value)
        coVerify(exactly = 2) { draftDao.upsert(any()) }
    }

    @Test
    fun `button and background saves write snapshots in order`() = runTest(testDispatcher) {
        val firstWriteCompleted = CompletableDeferred<Unit>()
        val writes = mutableListOf<FormV2Draft>()
        coEvery { draftDao.upsert(any()) } coAnswers {
            writes.add(firstArg())
            if (writes.size == 1) firstWriteCompleted.await()
        }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))
        viewModel.saveDraft()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Fever"))
        viewModel.saveDraft()
        runCurrent()

        assertEquals(1, writes.size)
        firstWriteCompleted.complete(Unit)
        runCurrent()
        assertEquals(listOf("Headache", "Fever"), writes.map { it.answers.single().answer.textAnswer })
        assertEquals(FormV2DraftSaveState.Saved, viewModel.draftSaveState.value)
    }

    @Test
    fun `unfinished required answers can be saved as a draft`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.saveDraft()
        runCurrent()

        assertEquals(FormV2DraftSaveState.Saved, viewModel.draftSaveState.value)
        coVerify { draftDao.upsert(match { it.answers.isEmpty() }) }
    }

    @Test
    fun `saving is unavailable before restoration finishes or without a patient`() = runTest(testDispatcher) {
        val restorationCompleted = CompletableDeferred<FormV2Draft?>()
        coEvery { draftDao.getDraft("patient-1", "template-1") } coAnswers { restorationCompleted.await() }
        val viewModel = createViewModel()
        runCurrent()
        assertFalse(viewModel.canSaveDraft)
        viewModel.saveDraft()
        runCurrent()
        coVerify(exactly = 0) { draftDao.upsert(any()) }

        restorationCompleted.complete(null)
        runCurrent()
        assertTrue(viewModel.canSaveDraft)

        val noPatientViewModel = createViewModel(patientId = null)
        runCurrent()
        assertFalse(noPatientViewModel.canSaveDraft)
        noPatientViewModel.saveDraft()
        runCurrent()
        coVerify(exactly = 0) { draftDao.upsert(any()) }
    }

    private fun createViewModel(patientId: String? = "patient-1") = FormV2RenderingViewModel(
        restApi,
        draftDao,
        SavedStateHandle(mapOf(
            FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID to "template-1",
            FormTemplateListV2Activity.EXTRA_PATIENT_ID to patientId,
        )),
    )

    private fun template() = FormTemplateV2(
        id = "template-1",
        version = 1,
        classification = FormClassificationV2("class-1", mapOf("english" to "Antenatal"), null),
        dateCreated = 1700000000L,
        questions = listOf(FormTemplateQuestionV2(
            id = "q1",
            formTemplateId = "template-1",
            questionType = QuestionTypeEnum.STRING,
            order = 0,
            questionText = mapOf("english" to "Symptoms"),
            questionStringId = null,
            categoryIndex = null,
            required = true,
            allowFutureDates = null,
            allowPastDates = null,
            stringMaxLength = null,
            stringMaxLines = null,
            numMin = null,
            numMax = null,
            units = null,
            userQuestionId = null,
            mcOptions = null,
        )),
    )
}
