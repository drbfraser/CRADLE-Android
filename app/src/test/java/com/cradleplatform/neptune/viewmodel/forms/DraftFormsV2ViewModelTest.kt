package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.Patient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DraftFormsV2ViewModelTest {

    private val draftDao = mockk<FormV2DraftDao>()
    private val patientManager = mockk<PatientManager>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun draftFor(patientId: String) = mockk<FormV2Draft> {
        every { this@mockk.patientId } returns patientId
    }

    private suspend fun awaitSuccess(viewModel: DraftFormsV2ViewModel): DraftFormsV2State.Success =
        viewModel.state.first { it is DraftFormsV2State.Success } as DraftFormsV2State.Success

    @Test
    fun `all-patients list has the name and patient number of each draft's patient`() = runTest {
        every { draftDao.observeAllDrafts() } returns
            flowOf(listOf(draftFor("uuid-1"), draftFor("uuid-2"), draftFor("uuid-1")))
        coEvery { patientManager.getPatientById("uuid-1") } returns
            Patient(id = "uuid-1", patientNumber = "12345", name = "AA")
        // Created on the web, so it has no patient number.
        coEvery { patientManager.getPatientById("uuid-2") } returns
            Patient(id = "uuid-2", patientNumber = null, name = "BB")

        val state = awaitSuccess(DraftFormsV2ViewModel(draftDao, patientManager, SavedStateHandle()))

        assertEquals(mapOf("uuid-1" to "AA", "uuid-2" to "BB"), state.patientNames)
        assertEquals(mapOf("uuid-1" to "12345", "uuid-2" to ""), state.patientNumbers)
    }

    @Test
    fun `single-patient list has no patient labels`() = runTest {
        every { draftDao.observeDraftsByPatientId("uuid-1") } returns flowOf(listOf(draftFor("uuid-1")))

        val state = awaitSuccess(
            DraftFormsV2ViewModel(
                draftDao,
                patientManager,
                SavedStateHandle(mapOf(DraftFormsV2ViewModel.EXTRA_PATIENT_ID to "uuid-1"))
            )
        )

        assertNull(state.patientNames)
        assertNull(state.patientNumbers)
    }
}
