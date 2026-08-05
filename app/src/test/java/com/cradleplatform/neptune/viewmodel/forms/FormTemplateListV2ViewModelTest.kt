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
