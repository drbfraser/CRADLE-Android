package com.cradleplatform.neptune.testutils

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.CommonPatientReadingJsons
import com.cradleplatform.neptune.model.CommonReadingJsons
import com.cradleplatform.neptune.model.Settings
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.mockito.Mockito.mock

object MockWebServerUtils {
    /**
     * Generic function to create a pair of [RestApi] and [MockWebServer] with given
     * [sharedPreferences] (mocked) if given, otherwise it will default to empty mocked one.
     *
     * The [webServerBlock] defines the functionalities for the RestApi and
     * allows for setting up the [MockWebServer].
     */
    fun createRestApiWithServerBlock(
        sharedPreferences: SharedPreferences? = null,
        webServerBlock: MockWebServer.() -> Unit
    ): Pair<RestApi, MockWebServer> {
        val mockServer = MockWebServer().apply { webServerBlock() }

        val mockSharedPrefs = sharedPreferences ?: mockk()

        val mockSettings = mockk<Settings> {
            every { networkHostname } returns mockServer.url("").host
            every { networkPort } returns mockServer.port.toString()
            every { networkUseHttps } returns false
        }

        val fakeUrlManager = UrlManager(mockSettings)

        val mockContext = mock(Context::class.java)

        val mockSmsStateReporter = mock(SmsStateReporter::class.java)
        val mockSmsSender = mock(SMSSender::class.java)
        val mockSmsDataProcessor = mock(SMSDataProcessor::class.java)

        val restApi = RestApi(
            mockContext,
            sharedPreferences = mockSharedPrefs,
            urlManager = fakeUrlManager,
            http = Http(mockContext),
            mockSmsStateReporter,
            mockSmsSender,
            mockSmsDataProcessor
        )

        return restApi to mockServer
    }

    /**
     * Creates a mocked [RestApi] instance with a [MockWebServer].
     * This instance simulates correct calls:
     *
     * 1) returns [MockResponse] 200 with correct Json strings with correct requests
     * 2) returns [MockResponse] 404 if requests to invalid path
     * 3) throws [NullPointerException] if request path is null
     *
     * The [RestApi] will use the given [sharedPreferences] (mocked) if given,
     * or default to empty mocked one.
     */
    fun createMockRestApi(
        sharedPreferences: SharedPreferences? = null,
    ) :Pair<RestApi, MockWebServer> {
        val (mockRestApi, mockServer) = createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{

                    val correctJsonString = getCorrectResponseJson(request.path!!)
                    if (correctJsonString == null){
                        setResponseCode(404)
                    } else {
                        setResponseCode(200)
                        setBody(correctJsonString)
                    }

                }
            }
        }

        return mockRestApi to mockServer
    }

    /**
     * Creates a mocked [RestApi] instance with a [MockWebServer] with IO failures.
     * This instance simulates errors that corrupts the json response body:
     *
     * 1) returns [MockResponse] 200 with invalid Json strings with correct requests
     * 2) returns [MockResponse] 404 if requests to invalid path
     * 3) throws [NullPointerException] if request path is null
     *
     * The [RestApi] will use the given [sharedPreferences] (mocked) if given,
     * or default to empty mocked one.
     */
    fun createMockRestApiWithIOFailures(
        sharedPreferences: SharedPreferences? = null,
    ) :Pair<RestApi, MockWebServer> {
        val (mockRestApi, mockServer) = createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{

                    val invalidJsonString = getIOErrorResponse(request.path!!)
                    if (invalidJsonString == null){
                        setResponseCode(404)
                    } else {
                        setResponseCode(200)
                        setBody(invalidJsonString)
                    }

                }
            }
        }
        return mockRestApi to mockServer
    }

    /**
     * Creates a mocked [RestApi] instance with a [MockWebServer] with authorization check.
     * This instance simulates correct calls and authorizations:
     *
     * Checks if auth token exist in the request header, if so:
     * 1) returns [MockResponse] 200 with correct Json strings with correct requests
     * 2) returns [MockResponse] 404 if requests to invalid path
     * 3) throws [NullPointerException] if request path is null
     *
     * The [RestApi] will use the given [sharedPreferences] (mocked) if given,
     * or default to empty mocked one.
     */
    fun createMockRestApiWithAuthCheck(
        sharedPreferences: SharedPreferences? = null,
        correctAuthToken: String
    ) :Pair<RestApi, MockWebServer> {
        val (mockRestApi, mockServer) = createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{

                    // Perform authorization check
                    if (request.headers["Authorization"] != "Bearer $correctAuthToken") {
                        setResponseCode(403)
                        setBody(request.headers.toString())
                        return@response
                    }

                    // prepare correct response
                    val correctJsonString = getCorrectResponseJson(request.path!!)
                    if (correctJsonString == null){
                        setResponseCode(404)
                    } else {
                        setResponseCode(200)
                        setBody(correctJsonString)
                    }

                }
            }
        }
        return mockRestApi to mockServer
    }

    /**
     * Creates a mocked [RestApi] instance with a [MockWebServer] with authorization check and IO failures.
     * This instance simulates authorization and errors that corrupts the json response body:
     *
     * Checks if auth token exist in the request header, if so:
     * 1) returns [MockResponse] 200 with invalid Json strings with correct requests
     * 2) returns [MockResponse] 404 if requests to invalid path
     * 3) throws [NullPointerException] if request path is null
     *
     * The [RestApi] will use the given [sharedPreferences] (mocked) if given,
     * or default to empty mocked one.
     */
    fun createMockRestApiWithAuthCheckAndIOFailure(
        sharedPreferences: SharedPreferences? = null,
        correctAuthToken: String
    ) :Pair<RestApi, MockWebServer> {
        val (mockRestApi, mockServer) = createRestApiWithServerBlock(sharedPreferences) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{

                    // Perform authorization check
                    if (request.headers["Authorization"] != "Bearer $correctAuthToken") {
                        setResponseCode(403)
                        setBody(request.headers.toString())
                        return@response
                    }

                    // prepare invalid response
                    val invalidJsonString = getIOErrorResponse(request.path!!)
                    if (invalidJsonString == null){
                        setResponseCode(404)
                    } else {
                        setResponseCode(200)
                        setBody(invalidJsonString)
                    }

                }
            }
        }
        return mockRestApi to mockServer
    }

    /**
     * returns a json [String] corresponding to the requested Api path [requestPath].
     *
     * returns "null" if [requestPath] is invalid/does not exist
     */
    private fun getCorrectResponseJson(requestPath:String): String? {
        var json:String? = null

        when (requestPath) {
            "/api/mobile/patients" -> {
                json = CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
            }
            "/api/mobile/readings" -> {
                json = CommonReadingJsons.allReadingsJsonExpectedPair.first
            }
        }
        return json
    }
    /**
     * returns a invalid json [String] corresponding to the requested Api path [requestPath]
     * to simulate a corrupted Json response due to IO error.
     *
     * returns "null" if [requestPath] is invalid/does not exist
     */
    private fun getIOErrorResponse(requestPath:String): String? {
        var json:String? = null
        when (requestPath){
            "/api/mobile/patients" -> {
                json = CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
                // mess it up somehow
                json = json.replace("\"patientId\"", "\"pattientId\"")
            }
            "/api/mobile/readings" -> {
                json = CommonReadingJsons.allReadingsJsonExpectedPair.first
                // mess it up somehow
                json = json.replace("\"readingId\"", "\"reaadingId\"")
            }
        }
        return json
    }



}