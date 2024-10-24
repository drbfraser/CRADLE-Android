package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

internal class RestApiTest {

    /**
     * A server where only the user "vht@vht.com" with "vht123" has actual data.
     */
    private val mockServer: MockWebServer
    private val restApi: RestApi
    init {
        val (api, server) = MockWebServerUtils.createRestApiWithServerBlock {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = when (request.path) {
                    "/api/user/auth" -> MockResponse().apply {
                        val login = JSONObject(request.body.readString(Charsets.UTF_8))
                        val (email, password) = try {
                            login.getString("email") to login.getString("password")
                        } catch (e: JSONException) {
                            setResponseCode(400)
                            setBody(
                                """
                                    {"message": "Bad request parameters"}
                                """.trimIndent()
                            )
                            return@apply
                        }

                        val response: JSONObject = try {
                            if (email == "vht@vht.com") {
                                if (password != "vht123") {
                                    throw GeneralSecurityException()
                                }

                                // sync with the actual endpoint
                                JSONObject("""
                            {
                                "email": "vht@vht.com",
                                "role": "VHT",
                                "firstName": "TestVHT",
                                "healthFacilityName": "H0000",
                                "phoneNumbers": ["666-666-6666", "777-777-7777", "555-555-5555"],
                                "isLoggedIn": true,
                                "userId": 3,
                                "token": "test-token",
                                "refresh": "test-refresh-token",
                                "smsKey": "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"
                            }
                            """.trimIndent())
                            } else {
                                throw GeneralSecurityException()
                            }
                        } catch (e: GeneralSecurityException) {
                            setResponseCode(401)
                            setBody(
                                JSONObject()
                                    .put("message", "Invalid email or password")
                                    .toString()
                            )
                            return@apply
                        }

                        setResponseCode(200)
                        setBody(response.toString())
                    }

                    else -> {
                        MockResponse().setResponseCode(404)
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
    fun authenticate() {
        val badLoginResult = runBlocking {
            restApi.authenticate("bad@email.com", "password")
        }
        check(badLoginResult is NetworkResult.Failure) { "got $badLoginResult" }
        assertEquals(401, badLoginResult.statusCode)
        assertEquals(
            "Invalid email or password",
            JSONObject(badLoginResult.body.decodeToString()).getString("message")
        )

        val goodLoginResult = runBlocking {
            restApi.authenticate("vht@vht.com", "vht123")
        }
        println("debug-test: $goodLoginResult")
        check(goodLoginResult is NetworkResult.Success) { "got $goodLoginResult" }
        assertEquals(200, goodLoginResult.statusCode)
        val loginResponse = goodLoginResult.value

        val expectedLoginResponseForVht = LoginResponse(
            email = "vht@vht.com",
            role = "VHT",
            firstName = "TestVHT",
            healthFacilityName = "H0000",
            userId = 3,
            token = "test-token",
            phoneNumbers = listOf<String>("666-666-6666", "777-777-7777", "555-555-5555"),
            smsKey = "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"
        )

        assertEquals(expectedLoginResponseForVht, loginResponse)
    }

    /*
    @Test
    fun getAllPatientsStreaming() {
    }

    @Test
    fun getPatient() {
    }

    @Test
    fun getPatientInfo() {
    }

    @Test
    fun searchForPatient() {
    }

    @Test
    fun getReading() {
    }

    @Test
    fun getAssessment() {
    }

    @Test
    fun postPatient() {
    }

    @Test
    fun putPatient() {
    }

    @Test
    fun postReading() {
    }

    @Test
    fun associatePatientToUser() {
    }

    @Test
    fun getAllHealthFacilities() {
    }

    @Test
    fun getUpdates() {
    }

     */
}