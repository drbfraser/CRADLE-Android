package com.cradleplatform.neptune.model

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ReferralTest {
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
    fun `jackson serialize And Deserialize for Dataclass Referral`() {
        val referral = CommonPatientReferralJsons.patientWithStandaloneReferral.second.referrals[0]

        val writer = JacksonMapper.writerForReferral
        val serialized = writer.writeValueAsString(referral)

        val reader = JacksonMapper.readerForReferral
        val deserializedReferral = reader.readValue<Referral>(serialized)

        assertEquals(referral, deserializedReferral)
    }

    @Test
    fun `post referral with success response`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/referrals" -> MockResponse().apply {
                            if (request.method == "POST") {
                                setResponseCode(200)
                                setBody("{\"message\": \"Referral created\"}")
                            } else {
                                setResponseCode(404)
                            }
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }

            val mapper = jacksonObjectMapper()
            val reader = mapper.readerFor(ServerResponse::class.java)

            val emptyJson = JSONObject()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = emptyJson.toString().toRequestBody(mediaType)

            val testRequest: NetworkResult<ServerResponse> = runBlocking {
                Http().makeRequest(
                    method = Http.Method.POST,
                    url = server.url("/api/referrals").toString(),
                    headers = emptyMap(),
                    requestBody = body,
                    inputStreamReader = { reader.readValue(it) }
                )
            }

            check(testRequest is NetworkResult.Success) { "expected testRequest to be success, but got $testRequest" }
            assertEquals(200, testRequest.statusCode)
        }
    }

    @Test
    fun `post referral with failure response`() {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/api/referrals" -> MockResponse().apply {
                            if (request.method == "POST") {
                                setResponseCode(400)
                            } else {
                                setResponseCode(404)
                            }
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
            }

            val mapper = jacksonObjectMapper()
            val reader = mapper.readerFor(ServerResponse::class.java)

            val emptyJson = JSONObject()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = emptyJson.toString().toRequestBody(mediaType)

            val failedRequest: NetworkResult<ServerResponse> = runBlocking {
                Http().makeRequest(
                    method = Http.Method.POST,
                    url = server.url("/api/referrals").toString(),
                    headers = emptyMap(),
                    requestBody = body,
                    inputStreamReader = { reader.readValue(it) }
                )
            }

            check(failedRequest is NetworkResult.Failure) {
                "expected failedRequest to be Failure, but got $failedRequest"
            }
            assertEquals(400, failedRequest.statusCode)
        }
    }
}