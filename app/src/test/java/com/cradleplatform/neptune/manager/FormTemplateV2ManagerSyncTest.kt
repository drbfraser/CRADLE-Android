package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.database.daos.FormTemplateV2Dao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateV2Entity
import com.cradleplatform.neptune.model.FormTemplateLanguagesV2Response
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class FormTemplateV2ManagerSyncTest {
    private val restApi = mockk<RestApi>()
    private val templateDao = mockk<FormTemplateV2Dao>()
    private val manager = FormTemplateV2Manager(restApi, templateDao)

    @BeforeEach
    fun setUp() {
        coEvery { templateDao.replaceAvailableTemplates(any()) } returns Unit
    }

    @Test
    fun `historical template languages are fetched without loading the current catalog`() = runTest {
        val oldTemplate = fullTemplate(version = 1).copy(availableLanguages = null)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity("template-1", oldTemplate)
        coEvery { templateDao.saveTemplate(any()) } returns Unit
        coEvery { restApi.getFormTemplateLanguagesV2("template-1") } returns
            NetworkResult.Success(FormTemplateLanguagesV2Response(listOf("English", "French")), 200)
        val result = manager.getSubmissionLanguages(oldTemplate) as NetworkResult.Success
        assertEquals(listOf("English", "French"), result.value)
        coVerify { templateDao.saveTemplate(match {
            it.template.version == 1 && it.template.availableLanguages == listOf("English", "French")
        }) }
        coVerify(exactly = 0) { restApi.getAllClassificationsSummaryV2() }
        coVerify(exactly = 0) { restApi.getFormTemplateV2(any()) }
    }

    @Test
    fun `cached languages do not require a request and failed legacy lookup preserves cache`() = runTest {
        val template = fullTemplate(version = 1)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity("template-1", template)
        assertTrue(manager.getSubmissionLanguages(template) is NetworkResult.Success)
        coVerify(exactly = 0) { restApi.getFormTemplateLanguagesV2(any()) }
        val oldTemplate = template.copy(availableLanguages = null)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity("template-1", oldTemplate)
        coEvery { restApi.getFormTemplateLanguagesV2("template-1") } returns NetworkResult.Failure(ByteArray(0), 403)
        val result = manager.getSubmissionLanguages(oldTemplate) as NetworkResult.Failure
        assertEquals(403, result.statusCode)
        coVerify(exactly = 0) { templateDao.saveTemplate(any()) }
    }

    @Test
    fun `sync reuses full cached template when version is unchanged`() = runTest {
        val cachedTemplate = fullTemplate(version = 2)
        coEvery { restApi.getAllClassificationsSummaryV2() } returns
            NetworkResult.Success(listOf(summary(version = 2)), 200)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity(
            id = "template-1",
            template = cachedTemplate
        )

        val result = manager.syncAvailableTemplates()

        assertTrue(result.networkResult is NetworkResult.Success)
        assertEquals(0, result.totalFormClassDownloaded)
        coVerify(exactly = 0) { restApi.getFormTemplateV2(any()) }
        coVerify { templateDao.replaceAvailableTemplates(listOf(FormTemplateV2Entity("template-1", cachedTemplate, true))) }
    }

    @Test
    fun `sync downloads and caches template when version changed`() = runTest {
        val updatedTemplate = fullTemplate(version = 3)
        coEvery { restApi.getAllClassificationsSummaryV2() } returns
            NetworkResult.Success(listOf(summary(version = 3)), 200)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity(
            id = "template-1",
            template = fullTemplate(version = 2)
        )
        coEvery { restApi.getFormTemplateV2("template-1") } returns
            NetworkResult.Success(updatedTemplate, 200)

        val result = manager.syncAvailableTemplates()

        assertTrue(result.networkResult is NetworkResult.Success)
        assertEquals(1, result.totalFormClassDownloaded)
        coVerify(exactly = 1) { restApi.getFormTemplateV2("template-1") }
        coVerify { templateDao.replaceAvailableTemplates(listOf(FormTemplateV2Entity("template-1", updatedTemplate, true))) }
    }

    @Test
    fun `sync keeps existing catalog when a required template download fails`() = runTest {
        coEvery { restApi.getAllClassificationsSummaryV2() } returns NetworkResult.Success(
            listOf(summary("template-1", 2), summary("template-2", 1)),
            200
        )
        coEvery { templateDao.getTemplate(any()) } returns null
        coEvery { restApi.getFormTemplateV2("template-1") } returns
            NetworkResult.Success(fullTemplate("template-1", 2), 200)
        coEvery { restApi.getFormTemplateV2("template-2") } returns
            NetworkResult.Failure(ByteArray(0), 503)

        val result = manager.syncAvailableTemplates()

        assertTrue(result.networkResult is NetworkResult.Failure)
        coVerify(exactly = 0) { templateDao.replaceAvailableTemplates(any()) }
    }

    @Test
    fun `sync adds original language spelling to older cached templates`() = runTest {
        val oldTemplate = fullTemplate(version = 2).copy(availableLanguages = null)
        coEvery { restApi.getAllClassificationsSummaryV2() } returns
            NetworkResult.Success(listOf(summary(version = 2)), 200)
        coEvery { templateDao.getTemplate("template-1") } returns FormTemplateV2Entity("template-1", oldTemplate)
        coEvery { restApi.getFormTemplateLanguagesV2("template-1") } returns
            NetworkResult.Success(FormTemplateLanguagesV2Response(listOf("English", "French")), 200)

        assertTrue(manager.syncAvailableTemplates().networkResult is NetworkResult.Success)
        coVerify { templateDao.replaceAvailableTemplates(match {
            it.single().template.availableLanguages == listOf("English", "French")
        }) }
        coVerify(exactly = 0) { restApi.getFormTemplateV2(any()) }
    }

    private fun summary(id: String = "template-1", version: Int) = FormTemplateV2(
        id = id,
        version = version,
        archived = false,
        classification = FormClassificationV2(id = "class-$id", name = mapOf("English" to id), nameStringId = null),
        dateCreated = 1,
        questions = null
    )

    private fun fullTemplate(id: String = "template-1", version: Int) = summary(id, version).copy(
        questions = emptyList(), availableLanguages = listOf("English")
    )
}
