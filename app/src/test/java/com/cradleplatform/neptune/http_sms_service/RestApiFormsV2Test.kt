package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Basic connectivity/parsing tests for the new V2 forms api methods added to [restApi],
 * following the same MockWebServer pattern as [RestApiTest]. These assert request
 * shape (url/method/body) and response parsing against fixed json fixtures
 */
internal class RestApiFormsV2Test {

    private val mockServer: MockWebServer
    private val restApi: RestApi
    private var lastRequest: RecordedRequest? = null

    init {
        val (api, server) = MockWebServerUtils.createRestApiWithServerBlock {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    lastRequest = request
                    val path = request.path ?: return MockResponse().setResponseCode(404)

                    return when {

                        // end point for creating a new submission
                        path == "/api/forms/v2/submissions" && request.method == "POST" ->
                            MockResponse().setResponseCode(201).setBody(SUBMISSION_JSON)
                        
                        // end point for getting a submission by id
                        path == "/api/forms/v2/submissions/sub-1" && request.method == "GET" ->
                            MockResponse().setResponseCode(200).setBody(SUBMISSION_WITH_ANSWERS_JSON)

                        // end point for updating a submission by id
                        path == "/api/forms/v2/submissions/sub-1" && request.method == "PATCH" ->
                            MockResponse().setResponseCode(200).setBody(SUBMISSION_JSON)

                        // end point for getting a submission by id that does not exist
                        path == "/api/forms/v2/submissions/does-not-exist" && request.method == "GET" ->
                            MockResponse().setResponseCode(404)
                                .setBody("""{"message": "No form with ID: does-not-exist."}""")

                        // end point for getting a list of templates
                        path == "/api/forms/v2/templates" && request.method == "GET" ->
                            MockResponse().setResponseCode(200).setBody(TEMPLATE_LIST_JSON)

                        // end point for getting a single template by id
                        path == "/api/forms/v2/templates/template-1" && request.method == "GET" ->
                            MockResponse().setResponseCode(200).setBody(TEMPLATE_JSON)
                        
                        // end point for getting a single template by id that does not exist
                        path == "/api/forms/v2/classifications/summary" && request.method == "GET" ->
                            MockResponse().setResponseCode(200).setBody("[$TEMPLATE_JSON]")
                        
                        // simple 404 error for everything else 
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        }
        restApi = api
        mockServer = server
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

     @Test
    fun postFormSubmissionV2() {
        val request = CreateFormSubmissionRequestV2(
            formTemplateId = "template-1",
            patientId = "patient-1",
            userId = 3,
            answers = listOf(
                FormAnswerV2(questionId = "q1", answer = AnswerV2.createTextAnswer("Normal")),
                FormAnswerV2(questionId = "q2", answer = AnswerV2.createMcAnswer(listOf(0, 2))),
            ),
        )

        val result = runBlocking { restApi.postFormSubmissionV2(request) }

        check(result is NetworkResult.Success) { "got $result" }
        assertEquals(201, result.statusCode)
        assertEquals("sub-1", result.value.id)
        assertEquals("template-1", result.value.formTemplateId)
        assertEquals(3, result.value.userId)

        // Verify the request body was serialized in the shape the backend expects.
        val sentBody = JSONObject(lastRequest!!.body.readString(Charsets.UTF_8))
        assertEquals("template-1", sentBody.getString("formTemplateId"))
        assertEquals("patient-1", sentBody.getString("patientId"))
        val sentAnswers = sentBody.getJSONArray("answers")
        assertEquals(2, sentAnswers.length())
        assertEquals("q1", sentAnswers.getJSONObject(0).getString("questionId"))
        assertEquals("Normal", sentAnswers.getJSONObject(0).getJSONObject("answer").getString("text"))
        val mcAnswer = sentAnswers.getJSONObject(1).getJSONObject("answer").getJSONArray("mcIdArray")
        assertEquals(0, mcAnswer.getInt(0))
        assertEquals(2, mcAnswer.getInt(1))
    }


    @Test
    fun getFormSubmissionV2() {
        val result = runBlocking { restApi.getFormSubmissionV2("sub-1") }

        check(result is NetworkResult.Success){ 
            "got $result" 
        }
        assertEquals(200, result.statusCode)
        assertEquals("sub-1", result.value.id)
        assertEquals(2, result.value.answers?.size)

        val mcAnswer = result.value.answers?.get(1)
        assertEquals("q2", mcAnswer?.questionId)
        assertEquals(listOf("Headache", "Fever", "Nausea"), mcAnswer?.mcOptions)
        assertEquals(listOf(0, 2), mcAnswer?.answer?.mcIdArrayAnswer)
    }



}
