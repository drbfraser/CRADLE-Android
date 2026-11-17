package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.daos.FormSubmissionV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.cradleplatform.neptune.model.FormSubmissionV2Entity
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
