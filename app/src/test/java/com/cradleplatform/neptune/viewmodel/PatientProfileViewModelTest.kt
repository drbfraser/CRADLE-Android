package com.cradleplatform.neptune.viewmodel

import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.WorkflowManager
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.utilities.extensions.InstantExecutorExtension
import com.cradleplatform.neptune.viewmodel.patients.PatientProfileViewModel
import io.mockk.coEvery
import io.mockk.every
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantExecutorExtension::class)
internal class PatientProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val restApi = mockk<RestApi>()
    private val patientManager = mockk<PatientManager>(relaxed = true)
    private val readingManager = mockk<ReadingManager>(relaxed = true)
    private val referralManager = mockk<ReferralManager>(relaxed = true)
    private val assessmentManager = mockk<AssessmentManager>(relaxed = true)
    private val formResponseManager = mockk<FormResponseManager>(relaxed = true)
    private val workflowManager = mockk<WorkflowManager>(relaxed = true)
    private val patient = mockk<Patient> { every { id } returns "patient-1" }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { patientManager.getPatientById("patient-1") } returns patient
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `local records load while V2 request is pending and forms arrive independently`() = runTest(dispatcher) {
        val response = CompletableDeferred<NetworkResult<List<FormSubmissionV2>>>()
        coEvery { restApi.getPatientFormSubmissionsV2("patient-1") } coAnswers { response.await() }
        val viewModel = createViewModel()
        viewModel.loadPatient("patient-1")
        runCurrent()

        assertEquals(patient, viewModel.patient.value)
        assertEquals(emptyList<Any>(), viewModel.submittedForms.value)
        assertNull(viewModel.submittedFormsV2.value)

        val forms = listOf(submission())
        response.complete(NetworkResult.Success(forms, 200))
        runCurrent()
        assertEquals(forms, viewModel.submittedFormsV2.value?.unwrapped)
    }

    @Test
    fun `refresh replaces forms with server result and exposes failures`() = runTest(dispatcher) {
        coEvery { restApi.getPatientFormSubmissionsV2("patient-1") } returnsMany listOf(
            NetworkResult.Success(listOf(submission()), 200),
            NetworkResult.Success(emptyList(), 200),
            NetworkResult.Failure(ByteArray(0), 403)
        )
        val viewModel = createViewModel()
        viewModel.loadPatient("patient-1")
        runCurrent()
        assertEquals(1, viewModel.submittedFormsV2.value?.unwrapped?.size)

        viewModel.refreshPatientData()
        runCurrent()
        assertEquals(emptyList<Any>(), viewModel.submittedFormsV2.value?.unwrapped)

        viewModel.refreshPatientData()
        runCurrent()
        val failure = viewModel.submittedFormsV2.value
        check(failure is NetworkResult.Failure)
        assertEquals(403, failure.statusCode)
        assertEquals(emptyList<Any>(), viewModel.submittedForms.value)
    }

    private fun createViewModel() = PatientProfileViewModel(
        patientManager, readingManager, referralManager, assessmentManager,
        formResponseManager, workflowManager, restApi
    )

    private fun submission() = FormSubmissionV2(
        "submission-1", "template-1", "patient-1", 3,
        1700000000L, 1700000000L, "English", false, "Antenatal"
    )
}
