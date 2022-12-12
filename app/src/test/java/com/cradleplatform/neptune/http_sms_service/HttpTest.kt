package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.TimeUnit

internal class HttpTest {

    data class ServerResponse(val message: String)

    @BeforeEach
    fun beforeEach() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `requestWithStream test success response`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/test" -> MockResponse().apply {
                            setResponseCode(200)
                            setBody("{\"message\": \"This is a test\"}")
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }

            val mapper = jacksonObjectMapper()
            val reader = mapper.readerFor(ServerResponse::class.java)

            val testRequest: NetworkResult<ServerResponse> = runBlocking {
                Http().makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/test").toString(),
                    headers = emptyMap(),
                    inputStreamReader = { reader.readValue(it) }
                )
            }

            check(testRequest is NetworkResult.Success) { "expected testRequest to be success, but got $testRequest" }
            assertEquals(200, testRequest.statusCode)
            assertEquals("This is a test", testRequest.value.message)
        }
    }

    @Test
    fun `requestWithStream test success but throttled response`() {
        MockWebServer().use { server ->
            val ipsum = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nam velit augue, fringilla non ex in,
ornare gravida odio. Integer ultricies, ligula in fringilla dignissim, sem dolor mattis elit, sit
amet varius magna purus in urna. In sit amet rutrum ante. Proin non auctor tellus. Pellentesque
finibus, nulla eu convallis viverra, ipsum lectus egestas tellus, vel accumsan magna ante et ante.
Donec in rutrum quam. Proin id quam ut odio scelerisque dictum. Curabitur a elit blandit dui dictum
fringilla eu at nisl. Aliquam semper vestibulum posuere. Integer tincidunt sollicitudin sem ut
elementum.

In finibus lorem tortor, eget vestibulum velit volutpat sed. Cras lacus odio, sollicitudin at nibh
sit amet, mattis ullamcorper mauris. In a neque arcu. Curabitur tristique enim orci, semper
vestibulum metus luctus sed. Aliquam dictum ex at facilisis hendrerit. Nulla fringilla eget odio sed
facilisis. Nam feugiat, justo ut porta tempus, mauris tortor placerat urna, ut tincidunt neque
tortor rutrum mauris. Morbi pellentesque ex.
            """.trimIndent()

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/test" -> MockResponse().apply {
                            setResponseCode(200)
                            setBody(JSONObject().put("message", ipsum).toString())
                            // 256 bytes per second to write 1024 bytes
                            throttleBody(bytesPerPeriod = 256, period = 1, TimeUnit.SECONDS)
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }

            val mapper = jacksonObjectMapper()
            val reader = mapper.readerFor(ServerResponse::class.java)

            val testRequest: NetworkResult<ServerResponse> = runBlocking {
                Http().makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/test").toString(),
                    headers = emptyMap(),
                    inputStreamReader = { reader.readValue(it) }
                )
            }

            check(testRequest is NetworkResult.Success) { "expected testRequest to be success, but got $testRequest" }
            assertEquals(200, testRequest.statusCode)
            assertEquals(ipsum, testRequest.value.message)
        }
    }

    @Test
    fun `requestWithStream test failure response`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/test/need-auth" -> MockResponse().apply {
                            if (request.getHeader("Authorization").isNullOrEmpty()) {
                                setResponseCode(422)
                                setBody("{\"message\": \"Signature verification failed\"}")
                            } else {
                                setResponseCode(200)
                            }
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }

            val http = Http()

            val notFoundRequest: NetworkResult<ServerResponse> = runBlocking {
                http.makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/unavailable/api/useless").toString(),
                    headers = emptyMap(),
                    inputStreamReader = {
                        // This shouldn't be called for failures.
                        fail(
                            "not supposed to read input stream using this reader, because" +
                                "the server might send a different response type than the type parameter"
                        )
                    }
                )
            }

            check(notFoundRequest is NetworkResult.Failure) {
                "expected notFoundRequest to be Failure, but got $notFoundRequest"
            }
            assertEquals(404, notFoundRequest.statusCode)


            val testRequest: NetworkResult<ServerResponse> = runBlocking {
                http.makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/test/need-auth").toString(),
                    headers = emptyMap(),
                    inputStreamReader = {
                        // This shouldn't be called for failures.
                        fail(
                            "not supposed to read input stream using this reader, because" +
                                "the server might send a different response type than the type parameter"
                        )
                    }
                )
            }

            check(testRequest is NetworkResult.Failure) { "expected testRequest to be Failure, but got $testRequest" }
            assertEquals(422, testRequest.statusCode)
            assertEquals(
                "Signature verification failed",
                JSONObject(testRequest.body.decodeToString()).get("message")
            )

            val testRequestWithAuthToken: NetworkResult<Unit> = runBlocking {
                http.makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/test/need-auth").toString(),
                    headers = mapOf("Authorization" to "Abc"),
                    inputStreamReader = {}
                )
            }

            check(testRequestWithAuthToken is NetworkResult.Success) {
                "expected testRequestWithAuthToken to be Success, but got $testRequestWithAuthToken\n" +
                    "with body ${(testRequestWithAuthToken as NetworkResult.Failure).body.decodeToString()}"
            }
            assertEquals(200, testRequestWithAuthToken.statusCode)
        }
    }

    @Test
    fun `requestWithStream test unexpected JSON response`() {
        MockWebServer().use { server ->
            // We expect JSON of the form {"message": "This is a message"},
            // so this will fail us.
            server.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                        {
                            "message": {
                                "this is a key": 545
                            }
                        }
                    """.trimIndent()
                )
            )

            val mapper = jacksonObjectMapper()
            val reader = mapper.readerFor(ServerResponse::class.java)
            // This should throw an exception during parsing
            val testRequest: NetworkResult<ServerResponse> = runBlocking {
                Http().makeRequest(
                    method = Http.Method.GET,
                    url = server.url("/api/anything").toString(),
                    headers = emptyMap(),
                    inputStreamReader = { reader.readValue(it) }
                )
            }
            check(testRequest is NetworkResult.NetworkException) {
                "expected testRequest to be NetworkException, but got $testRequest"
            }
            // "General exception type used as the base class for all JsonMappingExceptions that
            // are due to input not mapping to target definition"
            assert(testRequest.cause is MismatchedInputException)
        }
    }
}