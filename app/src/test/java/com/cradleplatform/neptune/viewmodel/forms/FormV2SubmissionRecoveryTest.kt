package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.database.FormV2DraftTypeConverters
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.database.daos.FormTemplateV2Dao
import com.cradleplatform.neptune.manager.FormSubmissionV2Manager
import com.cradleplatform.neptune.manager.FormTemplateV2Manager
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.AnswerWithQuestionV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateLanguagesV2Response
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.QuestionTypeEnum
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
internal class FormV2SubmissionRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var restApi: RestApi
    private lateinit var drafts: FormV2DraftDao
    private lateinit var templateDao: FormTemplateV2Dao
    private lateinit var submissionManager: FormSubmissionV2Manager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        restApi = mockk()
        drafts = mockk()
        templateDao = mockk()
        submissionManager = mockk()
        coEvery { submissionManager.cacheSubmittedForm(any(), any(), any()) } returns Unit
        coEvery { submissionManager.cacheVerifiedSubmission(any(), any(), any()) } returns Unit
        coEvery { templateDao.getTemplate(any()) } returns null
        coEvery { templateDao.saveTemplate(any()) } returns Unit
        coEvery { restApi.getFormTemplateV2("template-1") } returns NetworkResult.Success(template(), 200)
        coEvery { restApi.getFormTemplateLanguagesV2("template-1") } returns NetworkResult.Success(
            FormTemplateLanguagesV2Response(listOf("English", "French")), 200
        )
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft()
        coEvery { drafts.upsert(any()) } returns Unit
        coEvery { drafts.deleteDraft("patient-1", "template-1") } returns 1
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Failure(ByteArray(0), 409)
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(saved(), 200)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `restored draft language confirms a saved submission before querying template languages`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(language = "French")
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns
            NetworkResult.Success(saved().copy(lang = "French"), 200)
        coEvery { restApi.getFormTemplateLanguagesV2("template-1") } returns
            NetworkResult.NetworkException(IOException("Disconnected"))
        val viewModel = viewModel()
        runCurrent()
        assertEquals("French", viewModel.language)
        viewModel.submitForm()
        runCurrent()

        assertEquals("French", viewModel.language)
        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerify(exactly = 0) { restApi.getFormTemplateLanguagesV2("template-1") }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
    }

    @Test
    fun `a changed language does not discard a draft even when answers match`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(language = "French")
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertDraftKept(viewModel)
        coVerify { drafts.upsert(match { it.language == "French" }) }
        coVerify(exactly = 0) { restApi.getFormTemplateLanguagesV2("template-1") }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `a recreated ViewModel recovers a lost response using the persisted draft ID`() = runTest(dispatcher) {
        var storedDraft: FormV2Draft? = draft().copy(submissionId = null)
        val converters = FormV2DraftTypeConverters()
        coEvery { drafts.getDraft("patient-1", "template-1") } coAnswers { storedDraft }
        coEvery { drafts.upsert(any()) } coAnswers {
            val draft = firstArg<FormV2Draft>()
            // Round-trip the JSON fields that Room persists rather than sharing the answer objects.
            storedDraft = draft.copy(
                formTemplate = converters.jsonToFormTemplateV2(converters.formTemplateV2ToJson(draft.formTemplate)),
                answers = converters.jsonToFormAnswersV2(converters.formAnswersV2ToJson(draft.answers))
            )
        }
        val request = slot<CreateFormSubmissionRequestV2>()
        coEvery { restApi.postFormSubmissionV2(capture(request)) } returns
            NetworkResult.NetworkException(IOException("Response lost"))
        val original = viewModel()
        runCurrent()
        original.submitForm()
        runCurrent()
        assertDraftKept(original)
        val persistedId = requireNotNull(storedDraft?.submissionId)
        assertEquals(request.captured.id, persistedId)
        coEvery { restApi.getFormSubmissionV2(persistedId) } returns
            NetworkResult.Success(saved().copy(id = persistedId), 200)
        coEvery { drafts.deleteDraft("patient-1", "template-1") } coAnswers {
            storedDraft = null
            1
        }

        val recreated = viewModel()
        runCurrent()
        assertEquals("Headache", recreated.answerState.getAnswer("q1")?.textAnswer)
        recreated.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, recreated.submissionState.value)
        assertEquals(null, storedDraft)
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { restApi.getFormSubmissionV2(persistedId) }
        coVerify { submissionManager.cacheVerifiedSubmission(saved().copy(id = persistedId), any(), any()) }
        coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
        coVerify(exactly = 0) { restApi.patchFormSubmissionV2(any(), any()) }
    }

    @Test
    fun `failed verification after POST conflict keeps the draft without posting again`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(submissionId = null)
        for (status in listOf(404, 500)) {
            coEvery { restApi.getFormSubmissionV2(any()) } returns NetworkResult.Failure(ByteArray(0), status)
            val viewModel = viewModel()
            runCurrent()
            viewModel.submitForm()
            runCurrent()

            assertDraftKept(viewModel)
        }
        coVerify(exactly = 2) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 2) { restApi.getFormSubmissionV2(any()) }
    }

    @Test
    fun `restored submission ID is checked before another POST`() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        assertFalse(viewModel.canSubmit)
        coVerifyOrder {
            drafts.upsert(any())
            restApi.getFormSubmissionV2("submission-1")
            submissionManager.cacheVerifiedSubmission(any(), any(), any())
            drafts.deleteDraft("patient-1", "template-1")
        }
        viewModel.saveDraft()
        runCurrent()
        coVerify(exactly = 1) { drafts.upsert(any()) }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 0) { restApi.patchFormSubmissionV2(any(), any()) }
    }

    @Test
    fun `changed answers after a lost response stay saved under the original ID`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(submissionId = null)
        val firstRequest = slot<CreateFormSubmissionRequestV2>()
        val writes = mutableListOf<FormV2Draft>()
        coEvery { drafts.upsert(capture(writes)) } returns Unit
        coEvery { restApi.postFormSubmissionV2(capture(firstRequest)) } returns
            NetworkResult.NetworkException(IOException("Response lost"))
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
        assertDraftKept(viewModel)
        val originalId = requireNotNull(firstRequest.captured.id)
        assertFalse(originalId.isBlank())
        coEvery { restApi.getFormSubmissionV2(originalId) } returns
            NetworkResult.Success(saved().copy(id = originalId), 200)
        viewModel.answerState.setAnswer("q1", AnswerV2.createTextAnswer("Fever"))
        viewModel.submitForm()
        runCurrent()

        assertDraftKept(viewModel)
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { restApi.getFormSubmissionV2(originalId) }
        assertTrue(writes.isNotEmpty())
        writes.forEach { assertEquals(originalId, it.submissionId) }
        assertEquals("Fever", writes.last().answers.single().answer.textAnswer)
    }

    @Test
    fun `a newly answered optional question is not discarded or patched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q2", AnswerV2.createTextAnswer("New note"))
        viewModel.submitForm()
        runCurrent()

        assertDraftKept(viewModel)
        assertEquals("New note", viewModel.answerState.getAnswer("q2")?.textAnswer)
    }

    @Test
    fun `a cleared optional answer is not mistaken for a matching server form`() = runTest(dispatcher) {
        val original = saved()
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(
            original.copy(answers = original.answers.orEmpty() + original.answers.orEmpty().single().copy(
                id = "optional-answer", questionId = "q2", answer = AnswerV2.createTextAnswer("Old note")
            )), 200
        )
        val viewModel = viewModel()
        runCurrent()
        viewModel.answerState.setAnswer("q2", AnswerV2.createTextAnswer("Old note"))
        viewModel.answerState.removeAnswer("q2")
        viewModel.submitForm()
        runCurrent()

        assertDraftKept(viewModel)
        assertEquals(null, viewModel.answerState.getAnswer("q2"))
    }

    @Test
    fun `failed GET never proves that a submission is safe to delete`() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        for ((status, errorType) in listOf(
            401 to FormV2SubmissionErrorType.AUTH_ERROR,
            403 to FormV2SubmissionErrorType.PERMISSION_ERROR,
            500 to FormV2SubmissionErrorType.SERVER_ERROR
        )) {
            coEvery { restApi.getFormSubmissionV2("submission-1") } returns
                NetworkResult.Failure(ByteArray(0), status)
            viewModel.submitForm()
            runCurrent()
            assertDraftKept(viewModel)
            assertEquals(errorType, viewModel.submissionErrors.first())
        }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `network and parsing errors during GET keep the draft`() = runTest(dispatcher) {
        val viewModel = viewModel()
        runCurrent()
        coEvery { restApi.getFormSubmissionV2("submission-1") } throws IOException("Disconnected")
        viewModel.submitForm()
        runCurrent()
        assertDraftKept(viewModel)
        assertEquals(FormV2SubmissionErrorType.NETWORK_ERROR, viewModel.submissionErrors.first())

        coEvery { restApi.getFormSubmissionV2("submission-1") } throws IllegalStateException("Invalid JSON")
        viewModel.submitForm()
        runCurrent()
        assertDraftKept(viewModel)
        assertEquals(FormV2SubmissionErrorType.SERVER_ERROR, viewModel.submissionErrors.first())
    }

    @Test
    fun `a pending verification prevents another submit`() = runTest(dispatcher) {
        val response = CompletableDeferred<FormSubmissionWithAnswersV2>()
        coEvery { restApi.getFormSubmissionV2("submission-1") } coAnswers {
            NetworkResult.Success(response.await(), 200)
        }
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()
        assertFalse(viewModel.canSubmit)
        assertFalse(viewModel.canEditAnswers)
        viewModel.submitForm()
        runCurrent()
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { restApi.getFormSubmissionV2(any()) }
        response.complete(saved())
        runCurrent()
        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { restApi.getFormSubmissionV2(any()) }
        coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
    }

    @Test
    fun `queued saves cannot clear a newly assigned submission ID`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(submissionId = null)
        val firstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<FormV2Draft>()
        coEvery { drafts.upsert(any()) } coAnswers {
            writes.add(firstArg())
            if (writes.size == 1) firstWrite.await()
        }
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Failure(ByteArray(0), 500)
        val viewModel = viewModel()
        runCurrent()
        viewModel.saveDraft()
        runCurrent()
        viewModel.submitForm()
        runCurrent()
        viewModel.saveDraft()
        runCurrent()
        firstWrite.complete(Unit)
        runCurrent()

        assertEquals(3, writes.size)
        assertFalse(writes.last().submissionId.isNullOrBlank())
        assertEquals(writes[1].submissionId, writes.last().submissionId)
        assertFalse(writes[1].submissionTimeZoneId.isNullOrBlank())
        assertEquals(writes[1].submissionTimeZoneId, writes.last().submissionTimeZoneId)
    }

    @Test
    fun `DATE recovery uses the persisted submission zone after recreation`() = runTest(dispatcher) {
        checkRecoveryAcrossTimeZones(QuestionTypeEnum.DATE)
    }

    @Test
    fun `DATETIME recovery uses the persisted submission zone after recreation`() = runTest(dispatcher) {
        checkRecoveryAcrossTimeZones(QuestionTypeEnum.DATETIME)
    }

    @Test
    fun `edited date stays in the draft after switching time zones`() = runTest(dispatcher) {
        checkRecoveryAcrossTimeZones(QuestionTypeEnum.DATE, edited = true)
    }

    private suspend fun TestScope.checkRecoveryAcrossTimeZones(type: QuestionTypeEnum, edited: Boolean = false) {
        val previousZone = TimeZone.getDefault()
        try {
            val originalZone = TimeZone.getTimeZone("America/Vancouver")
            TimeZone.setDefault(originalZone)
            val template = template().copy(questions = listOf(
                template().questions.orEmpty().first().copy(questionType = type)
            ))
            val date = if (type == QuestionTypeEnum.DATE) "2000-01-01" else "2000-01-01 12:30"
            var stored = draft().copy(
                formTemplate = template,
                submissionId = null,
                answers = listOf(FormAnswerV2(questionId = "q1", answer = AnswerV2.createDateAnswer(date)))
            )
            coEvery { drafts.getDraft("patient-1", "template-1") } coAnswers { stored }
            coEvery { drafts.upsert(any()) } coAnswers { stored = firstArg() }
            val requests = mutableListOf<CreateFormSubmissionRequestV2>()
            coEvery { restApi.postFormSubmissionV2(capture(requests)) } throws IOException("Response lost")
            val original = viewModel()
            runCurrent()
            original.submitForm()
            runCurrent()
            assertDraftKept(original)
            assertEquals(originalZone.id, stored.submissionTimeZoneId)
            val request = requests.single()
            val localDate = LocalDate.of(2000, 1, 1)
            val expected = if (type == QuestionTypeEnum.DATE) {
                localDate.atStartOfDay(originalZone.toZoneId()).toEpochSecond()
            } else {
                localDate.atTime(12, 30).atZone(originalZone.toZoneId()).toEpochSecond()
            }
            assertEquals(expected.toString(), request.answers.single().answer.dateAnswer)
            coEvery { restApi.getFormSubmissionV2(requireNotNull(request.id)) } returns NetworkResult.Success(
                saved().copy(id = request.id, answers = listOf(saved().answers.orEmpty().single().copy(
                    questionType = type, answer = request.answers.single().answer
                ))), 200
            )
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            original.saveDraft()?.join()
            assertEquals(originalZone.id, stored.submissionTimeZoneId)
            val recreated = viewModel()
            runCurrent()
            if (edited) recreated.answerState.setAnswer("q1", AnswerV2.createDateAnswer("2000-01-02"))
            recreated.submitForm()
            runCurrent()
            if (edited) {
                assertDraftKept(recreated)
            } else {
                assertEquals(FormV2SubmissionState.Submitted, recreated.submissionState.value)
                coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
            }
            coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun `expired DATE can confirm a previously saved submission`() = runTest(dispatcher) {
        mockExpiredDate(QuestionTypeEnum.DATE)
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `expired DATETIME can confirm a previously saved submission`() = runTest(dispatcher) {
        mockExpiredDate(QuestionTypeEnum.DATETIME)
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `expired date recovery still rejects different server answers`() = runTest(dispatcher) {
        mockExpiredDate(QuestionTypeEnum.DATE)
        val different = saved().copy(answers = listOf(saved().answers.orEmpty().single().copy(
            questionType = QuestionTypeEnum.DATE, answer = AnswerV2.createDateAnswer("0")
        )))
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(different, 200)
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertDraftKept(viewModel)
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
    }

    @Test
    fun `404 still rejects expired dates instead of posting them`() = runTest(dispatcher) {
        for (type in listOf(QuestionTypeEnum.DATE, QuestionTypeEnum.DATETIME)) {
            mockExpiredDate(type)
            coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Failure(ByteArray(0), 404)
            val viewModel = viewModel()
            runCurrent()
            viewModel.submitForm()
            runCurrent()

            assertEquals(FormV2SubmissionState.ValidationError("q1", "Past dates are not allowed."),
                viewModel.submissionState.value)
            coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
            coVerify(exactly = 0) { drafts.deleteDraft(any(), any()) }
        }
    }

    @Test
    fun `a missing ID does not allow a new form to bypass date restrictions`() = runTest(dispatcher) {
        mockExpiredDate(QuestionTypeEnum.DATE, submissionId = null)
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertTrue(viewModel.submissionState.value is FormV2SubmissionState.ValidationError)
        coVerify(exactly = 0) { restApi.getFormSubmissionV2(any()) }
        coVerify(exactly = 0) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 0) { drafts.upsert(any()) }
    }

    @Test
    fun `404 with valid answers posts using the same ID`() = runTest(dispatcher) {
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Failure(ByteArray(0), 404)
        coEvery { restApi.postFormSubmissionV2(any()) } returns NetworkResult.Success(FormSubmissionV2(
            id = "submission-1", formTemplateId = "template-1", patientId = "patient-1", userId = 1,
            dateSubmitted = 0, lastEdited = 0, lang = "English", archived = false
        ), 201)
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerifyOrder {
            restApi.getFormSubmissionV2("submission-1")
            restApi.postFormSubmissionV2(match { it.id == "submission-1" })
            drafts.deleteDraft("patient-1", "template-1")
        }
    }

    @Test
    fun `a first POST returning 409 verifies the same submission before deleting the draft`() = runTest(dispatcher) {
        coEvery { drafts.getDraft("patient-1", "template-1") } returns draft().copy(submissionId = null)
        val firstRequest = slot<CreateFormSubmissionRequestV2>()
        coEvery { restApi.postFormSubmissionV2(capture(firstRequest)) } returns
            NetworkResult.Failure(ByteArray(0), 409)
        coEvery { restApi.getFormSubmissionV2(any()) } answers {
            NetworkResult.Success(saved().copy(id = requireNotNull(firstRequest.captured.id)), 200)
        }
        val viewModel = viewModel()
        runCurrent()
        viewModel.submitForm()
        runCurrent()

        assertEquals(FormV2SubmissionState.Submitted, viewModel.submissionState.value)
        coVerifyOrder {
            restApi.postFormSubmissionV2(any())
            restApi.getFormSubmissionV2(requireNotNull(firstRequest.captured.id))
            drafts.deleteDraft("patient-1", "template-1")
        }
        coVerify(exactly = 1) { restApi.postFormSubmissionV2(any()) }
        coVerify(exactly = 1) { restApi.getFormSubmissionV2(any()) }
        coVerify(exactly = 1) { drafts.deleteDraft("patient-1", "template-1") }
    }

    private fun mockExpiredDate(type: QuestionTypeEnum, submissionId: String? = "submission-1") {
        val template = template().copy(questions = listOf(
            template().questions.orEmpty().first().copy(questionType = type, allowPastDates = false)
        ))
        val value = if (type == QuestionTypeEnum.DATE) "2000-01-01" else "2000-01-01 12:30"
        val raw = listOf(FormAnswerV2(questionId = "q1", answer = AnswerV2.createDateAnswer(value)))
        // Server answers use Unix seconds; derive the fixture independently of the validator.
        // The mobile input represents a date/time in the device's local time zone.
        val date = LocalDate.of(2000, 1, 1)
        val zone = ZoneId.systemDefault()
        val serverSeconds = if (type == QuestionTypeEnum.DATE) {
            date.atStartOfDay(zone).toEpochSecond()
        } else {
            date.atTime(12, 30).atZone(zone).toEpochSecond()
        }
        coEvery { restApi.getFormTemplateV2("template-1") } returns NetworkResult.Success(template, 200)
        coEvery { drafts.getDraft("patient-1", "template-1") } returns
            draft().copy(formTemplate = template, answers = raw, submissionId = submissionId)
        val original = saved()
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(
            original.copy(answers = listOf(original.answers.orEmpty().single().copy(
                questionType = type, answer = AnswerV2.createDateAnswer(serverSeconds.toString())
            ))), 200
        )
    }

    private fun assertDraftKept(viewModel: FormV2RenderingViewModel) {
        assertTrue(viewModel.submissionState.value is FormV2SubmissionState.Error)
        assertTrue(viewModel.canSaveDraft)
        assertTrue(viewModel.canSubmit)
        coVerify(exactly = 0) { drafts.deleteDraft(any(), any()) }
        coVerify(exactly = 0) { restApi.patchFormSubmissionV2(any(), any()) }
    }

    private fun viewModel() = FormV2RenderingViewModel(
        restApi,
        drafts,
        FormTemplateV2Manager(restApi, templateDao),
        submissionManager,
        SavedStateHandle(mapOf(
            FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID to "template-1",
            FormTemplateListV2Activity.EXTRA_PATIENT_ID to "patient-1"
        ))
    )

    private fun draft() = FormV2Draft(
        patientId = "patient-1", formTemplateId = "template-1", formTemplateVersion = 1,
        formTemplate = template(), answers = listOf(FormAnswerV2(
            questionId = "q1", answer = AnswerV2.createTextAnswer("Headache")
        )), createdAt = 0, updatedAt = 0, submissionId = "submission-1"
    )

    private fun saved() = FormSubmissionWithAnswersV2(
        id = "submission-1", patientId = "patient-1", formTemplateId = "template-1", userId = 1,
        lang = "English", archived = false, dateSubmitted = 0, lastEdited = 0,
        answers = listOf(AnswerWithQuestionV2(
            id = "answer-1", questionId = "q1", formSubmissionId = "submission-1",
            answer = AnswerV2.createTextAnswer("Headache"), questionType = QuestionTypeEnum.STRING,
            questionText = "Symptoms", mcOptions = emptyList(), order = 0
        ))
    )

    private fun template(): FormTemplateV2 {
        val question = FormTemplateQuestionV2(
            id = "q1", formTemplateId = "template-1", questionType = QuestionTypeEnum.STRING,
            order = 0, questionText = mapOf("english" to "Symptoms"), questionStringId = null,
            categoryIndex = null, required = true, allowFutureDates = null, allowPastDates = null,
            stringMaxLength = null, stringMaxLines = null, numMin = null, numMax = null,
            units = null, userQuestionId = null, mcOptions = null
        )
        return FormTemplateV2(
            id = "template-1", version = 1,
            classification = FormClassificationV2("class-1", mapOf("english" to "Antenatal"), null),
            dateCreated = 1700000000L,
            questions = listOf(question, question.copy(id = "q2", required = false, order = 1))
        )
    }
}
