package com.cradleplatform.neptune.viewmodel.forms

import com.cradleplatform.neptune.database.daos.FormTemplateV2Dao
import com.cradleplatform.neptune.manager.FormTemplateV2Manager
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormTemplateV2Entity
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import com.cradleplatform.neptune.testutils.MockDependencyUtils
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * tests for FormTemplateListV2ViewModel: the network success path, and the
 * Room template cache used for offline selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FormTemplateListV2ViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var mockServer: MockWebServer? = null
    private val templateDao = mockk<FormTemplateV2Dao>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { templateDao.getAvailableTemplates() } returns emptyList()
        coEvery { templateDao.getTemplate(any()) } returns null
        coEvery { templateDao.saveTemplate(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        mockServer?.shutdown()
    }

    /**
     * Waits for a real (non-Loading) state. RestApi hops onto the real Dispatchers.IO for the
     * actual network call, which is genuine async work StandardTestDispatcher's virtual clock
     * can't fast-forward through - so this waits on the StateFlow emission itself instead of
     * relying on advanceUntilIdle(), which would return before that real work finishes.
     */
    private suspend fun awaitResult(viewModel: FormTemplateListV2ViewModel): FormTemplateListV2State =
        viewModel.state.first { it !is FormTemplateListV2State.Loading }

    @Test
    fun `loadTemplates succeeds and shows the templates from the server`() = runTest(testDispatcher) {
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (restApi, server) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when (request.path) {
                    "/api/forms/v2/templates" -> MockResponse().setResponseCode(200).setBody(TEMPLATE_LIST_JSON)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        mockServer = server

        val viewModel = FormTemplateListV2ViewModel(FormTemplateV2Manager(restApi, templateDao))
        val state = awaitResult(viewModel)

        check(state is FormTemplateListV2State.Success) { "got $state" }
        assertEquals(1, state.templates.size)
        assertEquals("Antenatal", state.templates.first().name)

        viewModel.selectedLanguage = "english"
        viewModel.selectTemplate(state.templates.first().id)
        assertEquals(null, viewModel.selectedLanguage)
        assertEquals(state.templates.first().id, viewModel.selectedTemplateId)
        val detailState = viewModel.selectedTemplate.first {
            it is FormTemplateDetailV2State.Error
        }
        check(detailState is FormTemplateDetailV2State.Error)
    }

    @Test
    fun `selectTemplate loads details successfully and clears previous language`() =
        runTest(testDispatcher) {
            val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()

            val (restApi, server) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
                dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) =
                        when (request.path) {
                            "/api/forms/v2/templates" ->
                                MockResponse().setResponseCode(200).setBody(TEMPLATE_LIST_JSON)

                            "/api/forms/v2/templates/template-1" ->
                                MockResponse().setResponseCode(200).setBody(TEMPLATE_DETAIL_JSON)

                            else -> MockResponse().setResponseCode(404)
                        }
                }
            }
            mockServer = server
            val viewModel = FormTemplateListV2ViewModel(FormTemplateV2Manager(restApi, templateDao))
            val listState = awaitResult(viewModel)
            check(listState is FormTemplateListV2State.Success) {
                "got $listState"
            }

            viewModel.selectedLanguage = "english"
            viewModel.selectTemplate(listState.templates.first().id)

            assertEquals("template-1", viewModel.selectedTemplateId)
            assertEquals(null, viewModel.selectedLanguage)
            assertEquals(
                FormTemplateDetailV2State.Loading,
                viewModel.selectedTemplate.value
            )

            val detailState = viewModel.selectedTemplate.first {
                it != null && it !is FormTemplateDetailV2State.Loading
            }

            check(detailState is FormTemplateDetailV2State.Success) {
                "got $detailState, expected successful template details"
            }
            assertEquals("template-1", detailState.template.id)
            assertEquals(1, detailState.template.version)
            assertEquals(
                "Antenatal",
                detailState.template.classification.name["english"]
            )
        }

    @Test
    fun `cached templates support listing and selection without network requests`() = runTest(testDispatcher) {
        val template = Gson().fromJson(TEMPLATE_DETAIL_JSON, FormTemplateV2::class.java)
        val cachedTemplate = FormTemplateV2Entity(template.id, template, availableForNewForms = true)
        coEvery { templateDao.getAvailableTemplates() } returns listOf(cachedTemplate)
        coEvery { templateDao.getTemplate(template.id) } returns cachedTemplate
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (failingRestApi, failingServer) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
            }
        }
        mockServer = failingServer

        val viewModel = FormTemplateListV2ViewModel(FormTemplateV2Manager(failingRestApi, templateDao))
        val state = awaitResult(viewModel)

        check(state is FormTemplateListV2State.Success) { "got $state, expected cached templates" }
        assertEquals(1, state.templates.size)
        assertEquals("Antenatal", state.templates.first().name)

        viewModel.selectedLanguage = "english"
        viewModel.selectTemplate(state.templates.first().id)
        assertEquals(null, viewModel.selectedLanguage)
        assertEquals(state.templates.first().id, viewModel.selectedTemplateId)
        val detailState = viewModel.selectedTemplate.first {
            it is FormTemplateDetailV2State.Success
        }
        check(detailState is FormTemplateDetailV2State.Success)
        assertEquals(template, detailState.template)
        assertEquals(0, failingServer.requestCount)
    }

    @Test
    fun `loadTemplates shows an error when the network fails and there is no cache`() = runTest(testDispatcher) {
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (restApi, server) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
            }
        }
        mockServer = server

        val viewModel = FormTemplateListV2ViewModel(FormTemplateV2Manager(restApi, templateDao))
        val state = awaitResult(viewModel)

        check(state is FormTemplateListV2State.Error) { "got $state" }
    }

    companion object {
        private const val TEMPLATE_LIST_JSON = """
            {
                "templates": [
                    {
                        "id": "template-1",
                        "formClassificationId": "class-1",
                        "version": 1,
                        "archived": false,
                        "name": "Antenatal",
                        "dateCreated": 1700000000
                    }
                ]
            }
        """

        private const val TEMPLATE_DETAIL_JSON = """
            {
                "id": "template-1",
                "version": 1,
                "archived": false,
                "classification": {
                    "id": "class-1",
                    "name": {
                        "english": "Antenatal"
                    },
                    "nameStringId": null
                },
                "dateCreated": 1700000000,
                "questions": []
            }
        """
    }
}
