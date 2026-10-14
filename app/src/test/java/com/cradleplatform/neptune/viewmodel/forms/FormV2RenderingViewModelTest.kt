package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormSubmissionV2
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
        coEvery { draftDao.deleteDraft("patient-1", "template-1") } returns 1
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

    @Test
    fun `successful submission persists its ID then removes the draft`() = runTest(testDispatcher) {
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Success(submission(), 201)
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        assertFalse(viewModel.canSaveDraft)
        coVerify {
            restApi.postFormSubmissionV2(match {
                !it.id.isNullOrBlank() &&
                    it.patientId == "patient-1" &&
                    it.formTemplateId == "template-1" &&
                    it.userId == null &&
                    it.answers.single().answer.textAnswer == "Headache"
            })
        }
        coVerify { draftDao.deleteDraft("patient-1", "template-1") }

        viewModel.saveDraft()
        runCurrent()
        coVerify(exactly = 1) { draftDao.upsert(any()) }
    }

    @Test
    fun `failed submission retains the draft and reuses its submission ID`() = runTest(testDispatcher) {
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Failure(ByteArray(0), 500)
        val requests = mutableListOf<CreateFormSubmissionRequestV2>()
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.submitForm()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Error("Server error (500)"), viewModel.submissionState.value)
        assertTrue(viewModel.canSaveDraft)
        coVerify(exactly = 0) { draftDao.deleteDraft(any(), any()) }
        coVerify { restApi.postFormSubmissionV2(capture(requests)) }
        assertEquals(2, requests.size)
        assertEquals(1, requests.map { it.id }.distinct().size)
        coVerify(exactly = 2) { draftDao.upsert(any()) }
    }

    @Test
    fun `validation failure does not save or post a submission`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.submitForm()
        runCurrent()

        assertEquals(
            FormV2SubmissionState.ValidationError("q1", "An answer is required."),
            viewModel.submissionState.value
        )
        coVerify(exactly = 0) { draftDao.upsert(any()) }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `pending submission locks editing and ignores repeated submit until failure`() = runTest(testDispatcher) {
        val response = CompletableDeferred<NetworkResult<FormSubmissionV2>>()
        coEvery { restApi.postFormSubmissionV2(any()) } coAnswers { response.await() }
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.submitForm()
        assertFalse(viewModel.canEditAnswers)
        assertFalse(viewModel.canSubmit)
        viewModel.submitForm()
        runCurrent()
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }

        response.complete(NetworkResult.Failure(ByteArray(0), 500))
        runCurrent()
        assertTrue(viewModel.canEditAnswers)
        assertTrue(viewModel.canSubmit)
        assertEquals("Headache", viewModel.answerState.getAnswer("q1")?.textAnswer)
        coVerify(exactly = 0) { draftDao.deleteDraft(any(), any()) }
    }

    @Test
    fun `successful POST with cleanup failure cannot be edited or submitted again`() = runTest(testDispatcher) {
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Success(submission(), 201)
        coEvery { draftDao.deleteDraft(any(), any()) } throws IllegalStateException("Database unavailable")
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.submitForm()
        runCurrent()

        assertEquals(
            FormV2SubmissionState.Error("Form submitted, but the local draft could not be removed."),
            viewModel.submissionState.value
        )
        assertFalse(viewModel.canEditAnswers)
        assertFalse(viewModel.canSubmit)
        assertFalse(viewModel.canSaveDraft)
        viewModel.submitForm()
        runCurrent()
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `failed draft write prevents POST and unlocks editing`() = runTest(testDispatcher) {
        coEvery { draftDao.upsert(any()) } throws IllegalStateException("Database unavailable")
        val viewModel = createViewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Headache"))

        viewModel.submitForm()
        runCurrent()

        assertEquals(
            FormV2SubmissionState.Error("Unable to save form before submission."),
            viewModel.submissionState.value
        )
        assertTrue(viewModel.canEditAnswers)
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
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

    private fun submission() = FormSubmissionV2(
        id = "submission-1",
        formTemplateId = "template-1",
        patientId = "patient-1",
        userId = 1,
        dateSubmitted = 0,
        lastEdited = 0,
        lang = "English",
        archived = false,
    )
}
