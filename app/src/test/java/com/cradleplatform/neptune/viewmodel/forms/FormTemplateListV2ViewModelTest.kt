package com.cradleplatform.neptune.viewmodel.forms

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
 * SharedPreferences cache fallback when the network fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FormTemplateListV2ViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private var mockServer: MockWebServer? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
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

        val viewModel = FormTemplateListV2ViewModel(restApi, sharedPreferences)
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
            val viewModel = FormTemplateListV2ViewModel(restApi, sharedPreferences)
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
    fun `loadTemplates falls back to the cached list when the network fails`() = runTest(testDispatcher) {
        // first, a successful fetch to populate the cache.
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (successRestApi, successServer) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(200).setBody(TEMPLATE_LIST_JSON)
            }
        }
        val warmUpViewModel = FormTemplateListV2ViewModel(successRestApi, sharedPreferences)
        awaitResult(warmUpViewModel)
        successServer.shutdown()

        // second ViewModel, same cached prefs, but the network now fails.
        val (failingRestApi, failingServer) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
            }
        }
        mockServer = failingServer

        val viewModel = FormTemplateListV2ViewModel(failingRestApi, sharedPreferences)
        val state = awaitResult(viewModel)

        check(state is FormTemplateListV2State.Success) { "got $state, expected cache fallback" }
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
    fun `loadTemplates shows an error when the network fails and there is no cache`() = runTest(testDispatcher) {
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (restApi, server) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
            }
        }
        mockServer = server

        val viewModel = FormTemplateListV2ViewModel(restApi, sharedPreferences)
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
