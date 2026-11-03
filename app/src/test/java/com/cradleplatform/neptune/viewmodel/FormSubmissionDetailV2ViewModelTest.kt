package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.forms.FormSubmissionDetailV2State
import com.cradleplatform.neptune.viewmodel.forms.FormSubmissionDetailV2ViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class FormSubmissionDetailV2ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val restApi = mockk<RestApi>()
    private val patientManager = mockk<PatientManager>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `detail loads the exact template bound to the submission`() = runTest(dispatcher) {
        val submission = submission("patient-1")
        val template = mockk<FormTemplateV2>()
        val patient = mockk<Patient> { every { name } returns "AA" }
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(submission, 200)
        coEvery { restApi.getFormTemplateV2("original-template") } returns NetworkResult.Success(template, 200)
        coEvery { patientManager.getPatientById("patient-1") } returns patient

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(FormSubmissionDetailV2State.Success(submission, template, "AA"), viewModel.state.value)
        coVerify(exactly = 1) { restApi.getFormTemplateV2("original-template") }
    }

    @Test
    fun `missing local patient does not prevent viewing the submission`() = runTest(dispatcher) {
        val submission = submission("patient-1")
        val template = mockk<FormTemplateV2>()
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns NetworkResult.Success(submission, 200)
        coEvery { restApi.getFormTemplateV2("original-template") } returns NetworkResult.Success(template, 200)
        coEvery { patientManager.getPatientById("patient-1") } returns null

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(FormSubmissionDetailV2State.Success(submission, template, null), viewModel.state.value)
    }

    @Test
    fun `another patient's submission is rejected before loading its template`() = runTest(dispatcher) {
        coEvery { restApi.getFormSubmissionV2("submission-1") } returns
            NetworkResult.Success(submission("patient-2"), 200)

        val viewModel = createViewModel()
        runCurrent()

        assertEquals(FormSubmissionDetailV2State.Error, viewModel.state.value)
        coVerify(exactly = 0) { restApi.getFormTemplateV2(any()) }
        coVerify(exactly = 0) { patientManager.getPatientById(any()) }
    }

    private fun submission(patient: String) = mockk<FormSubmissionWithAnswersV2> {
        every { id } returns "submission-1"
        every { patientId } returns patient
        every { formTemplateId } returns "original-template"
    }

    private fun createViewModel() = FormSubmissionDetailV2ViewModel(
        restApi,
        patientManager,
        SavedStateHandle(mapOf(
            FormSubmissionDetailV2ViewModel.EXTRA_SUBMISSION_ID to "submission-1",
            FormSubmissionDetailV2ViewModel.EXTRA_PATIENT_ID to "patient-1"
        ))
    )
}
