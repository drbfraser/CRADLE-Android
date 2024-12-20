package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.manager.LoginResponseSmsKey
import com.cradleplatform.neptune.manager.LoginResponseUser
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
     * A server where only the user "vht@email.com" with password "cradle-vht" has actual data.
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
                            login.getString("username") to login.getString("password")
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
                            if (email == "vht@email.com") {
                                if (password != "cradle-vht") {
                                    throw GeneralSecurityException()
                                }

                                // sync with the actual endpoint
                                JSONObject("""
                            {
                                "user": {
                                    "email": "vht@vht.com",
                                    "username": "vht",
                                    "role": "VHT",
                                    "name": "TestVHT",
                                    "healthFacilityName": "H0000",
                                    "phoneNumbers": ["+1-666-666-6666", "+1-777-777-7777", "+1-555-555-5555"],
                                    "isLoggedIn": true,
                                    "id": 3,
                                }
                                "accessToken": "test-token",
                                "smsKey": {
                                    "key": "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"
                                }
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
            restApi.authenticate("vht@email.com", "cradle-vht")
        }
        println("debug-test: $goodLoginResult")
        check(goodLoginResult is NetworkResult.Success) { "got $goodLoginResult" }
        assertEquals(200, goodLoginResult.statusCode)
        val loginResponse = goodLoginResult.value

        val user = LoginResponseUser(
            email = "vht@email.com",
            role = "VHT",
            username = "vht",
            name = "TestVHT",
            healthFacilityName = "H0000",
            id = 3,
            phoneNumbers = listOf<String>("+1-666-666-6666", "+1777-777-7777", "+1555-555-5555"),
            smsKey = LoginResponseSmsKey(
                key = "{\"sms_key\":\"SGVsbG8sIFdvcmxkIQ==\"}"
            )
        )
        val expectedLoginResponseForVht = LoginResponse(
            user = user,
            accessToken = "test-token",
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