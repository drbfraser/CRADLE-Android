package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.manager.FormSubmissionV2Manager
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.viewmodel.forms.SubmittedFormsV2State
import com.cradleplatform.neptune.viewmodel.forms.SubmittedFormsV2ViewModel
import io.mockk.coEvery
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class SubmittedFormsV2ViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val submissionManager = mockk<FormSubmissionV2Manager>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { submissionManager.getCachedSubmissions("patient-1") } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pending request shows loading then submissions sorted by submission date`() = runTest(dispatcher) {
        val response = CompletableDeferred<NetworkResult<Unit>>()
        coEvery { submissionManager.syncPatientSubmissions("patient-1") } coAnswers { response.await() }
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Loading, viewModel.state.value)

        val older = submission()
        val newer = older.copy(id = "submission-2", dateSubmitted = older.dateSubmitted + 60)
        coEvery { submissionManager.getCachedSubmissions("patient-1") } returns listOf(newer, older)
        response.complete(NetworkResult.Success(Unit, 200))
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(listOf(newer, older)), viewModel.state.value)
    }

    @Test
    fun `request failure remains an error until retry returns an empty list`() = runTest(dispatcher) {
        coEvery { submissionManager.syncPatientSubmissions("patient-1") } returnsMany listOf(
            NetworkResult.Failure(ByteArray(0), 503),
            NetworkResult.Success(Unit, 200)
        )
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Error, viewModel.state.value)

        viewModel.loadForms()
        assertEquals(SubmittedFormsV2State.Loading, viewModel.state.value)
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(emptyList()), viewModel.state.value)
    }

    @Test
    fun `offline refresh retains locally stored forms`() = runTest(dispatcher) {
        coEvery { submissionManager.getCachedSubmissions("patient-1") } returns listOf(submission())
        val response = CompletableDeferred<NetworkResult<Unit>>()
        coEvery { submissionManager.syncPatientSubmissions("patient-1") } coAnswers { response.await() }
        val viewModel = createViewModel()
        runCurrent()
        response.complete(NetworkResult.NetworkException(java.io.IOException("Offline")))
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(listOf(submission())), viewModel.state.value)
    }

    @Test
    fun `cached forms appear before refresh and are hidden when access is rejected`() = runTest(dispatcher) {
        coEvery { submissionManager.getCachedSubmissions("patient-1") } returns listOf(submission())
        val response = CompletableDeferred<NetworkResult<Unit>>()
        coEvery { submissionManager.syncPatientSubmissions("patient-1") } coAnswers { response.await() }
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(listOf(submission())), viewModel.state.value)

        response.complete(NetworkResult.Failure(ByteArray(0), 403))
        runCurrent()
        assertEquals(SubmittedFormsV2State.PermissionError, viewModel.state.value)

        coEvery { submissionManager.syncPatientSubmissions("patient-1") } returns
            NetworkResult.Failure(ByteArray(0), 401)
        viewModel.loadForms()
        runCurrent()
        assertEquals(SubmittedFormsV2State.AuthenticationError, viewModel.state.value)
    }

    @Test
    fun `temporary server failure retains cached forms`() = runTest(dispatcher) {
        coEvery { submissionManager.getCachedSubmissions("patient-1") } returns listOf(submission())
        coEvery { submissionManager.syncPatientSubmissions("patient-1") } returns
            NetworkResult.Failure(ByteArray(0), 503)
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(listOf(submission())), viewModel.state.value)
    }

    private fun createViewModel() = SubmittedFormsV2ViewModel(
        submissionManager,
        SavedStateHandle(mapOf(SubmittedFormsV2ViewModel.EXTRA_PATIENT_ID to "patient-1"))
    )

    private fun submission() = FormSubmissionV2(
        "submission-1", "template-1", "patient-1", 3,
        1700000000L, 1700000000L, "English", false, "Antenatal"
    )
}
