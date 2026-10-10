package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
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

        val viewModel = FormTemplateListV2ViewModel(restApi, sharedPreferences, SavedStateHandle())
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
    fun `loadTemplates falls back to the cached list when the network fails`() = runTest(testDispatcher) {
        // first, a successful fetch to populate the cache.
        val (_, sharedPreferences) = MockDependencyUtils.createMockSharedPreferences()
        val (successRestApi, successServer) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(200).setBody(TEMPLATE_LIST_JSON)
            }
        }
        val warmUpViewModel = FormTemplateListV2ViewModel(successRestApi, sharedPreferences, SavedStateHandle())
        awaitResult(warmUpViewModel)
        successServer.shutdown()

        // second ViewModel, same cached prefs, but the network now fails.
        val (failingRestApi, failingServer) = MockWebServerUtils.createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(500)
            }
        }
        mockServer = failingServer

        val viewModel = FormTemplateListV2ViewModel(failingRestApi, sharedPreferences, SavedStateHandle())
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

        val viewModel = FormTemplateListV2ViewModel(restApi, sharedPreferences, SavedStateHandle())
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
    }
}
