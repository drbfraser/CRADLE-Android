package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
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
    private val restApi = mockk<RestApi>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pending request shows loading then submissions sorted by submission date`() = runTest(dispatcher) {
        val response = CompletableDeferred<NetworkResult<List<FormSubmissionV2>>>()
        coEvery { restApi.getPatientFormSubmissionsV2("patient-1") } coAnswers { response.await() }
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Loading, viewModel.state.value)

        val older = submission()
        val newer = older.copy(id = "submission-2", dateSubmitted = older.dateSubmitted + 60)
        response.complete(NetworkResult.Success(listOf(older, newer), 200))
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(listOf(newer, older)), viewModel.state.value)
    }

    @Test
    fun `request failure remains an error until retry returns an empty list`() = runTest(dispatcher) {
        coEvery { restApi.getPatientFormSubmissionsV2("patient-1") } returnsMany listOf(
            NetworkResult.Failure(ByteArray(0), 403),
            NetworkResult.Success(emptyList(), 200)
        )
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(SubmittedFormsV2State.Error, viewModel.state.value)

        viewModel.loadForms()
        assertEquals(SubmittedFormsV2State.Loading, viewModel.state.value)
        runCurrent()
        assertEquals(SubmittedFormsV2State.Success(emptyList()), viewModel.state.value)
    }

    private fun createViewModel() = SubmittedFormsV2ViewModel(
        restApi,
        SavedStateHandle(mapOf(SubmittedFormsV2ViewModel.EXTRA_PATIENT_ID to "patient-1"))
    )

    private fun submission() = FormSubmissionV2(
        "submission-1", "template-1", "patient-1", 3,
        1700000000L, 1700000000L, "English", false, "Antenatal"
    )
}
