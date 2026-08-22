package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.manager.LoginResponseUser
import com.cradleplatform.neptune.manager.SmsKey
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import com.cradleplatform.neptune.utilities.Protocol
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
import java.math.BigInteger
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
                                        "email": "vht@email.com",
                                        "username": "vht",
                                        "role": "VHT",
                                        "name": "TestVHT",
                                        "healthFacilityName": "H0000",
                                        "phoneNumbers": ["+1-666-666-6666", "+1-777-777-7777", "+1-555-555-5555"],
                                        "id": 3,
                                        "smsKey": {
                                            "key": "SGVsbG8sIFdvcmxkIQ==",
                                            "expiryDate": "2200-01-01 00:00:00",
                                            "message": "NORMAL",
                                            "staleDate": "2100-01-01 00:00:00"
                                        }
                                    },
                                    "accessToken": "test-token",

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
            phoneNumbers = listOf<String>("+1-666-666-6666", "+1-777-777-7777", "+1-555-555-5555"),
            smsKey = SmsKey(
                key = "SGVsbG8sIFdvcmxkIQ==",
                expiryDate = "2200-01-01 00:00:00",
                message = "NORMAL",
                staleDate = "2100-01-01 00:00:00"
            )
        )
        val expectedLoginResponseForVht = LoginResponse(
            user = user,
            accessToken = "test-token",
        )

        assertEquals(expectedLoginResponseForVht, loginResponse)
    }

    private fun sampleEditablePatient() = Patient(
        id = "123456",
        name = "Test Patient",
        dateOfBirth = "1994-05-20",
        isExactDateOfBirth = true,
        sex = Sex.FEMALE,
        isPregnant = true,
        gestationalAge = GestationalAgeWeeks(BigInteger.valueOf(1596091500)),
        pregnancyId = 77,
        zone = "37",
        villageNumber = "200",
        householdNumber = "20",
        drugHistory = "previous drug history",
        medicalHistory = "previous medical history",
        allergy = "peanuts",
        isArchived = false
    )

    private fun restApiCapturing(responseBody: String = "{}"): Pair<RestApi, MockWebServer> =
        MockWebServerUtils.createRestApiWithServerBlock {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(200).setBody(responseBody)
            }
        }

    @Test
    fun putPatient_sendsPersonalInfoFieldsInSnakeCase() {
        val (api, server) = restApiCapturing()
        val patient = sampleEditablePatient()

        val result = runBlocking { api.putPatient(patient, Protocol.HTTP) }
        check(result is NetworkResult.Success) { "got $result" }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/patients/${patient.id}/info", request.path)

        val body = JSONObject(request.body.readString(Charsets.UTF_8))
        assertEquals(patient.id, body.getString("id"))
        assertEquals(patient.name, body.getString("name"))
        assertEquals(patient.sex.name, body.getString("sex"))
        assertEquals(patient.dateOfBirth, body.getString("date_of_birth"))
        assertEquals(patient.isExactDateOfBirth, body.getBoolean("is_exact_date_of_birth"))
        assertEquals(patient.isPregnant, body.getBoolean("is_pregnant"))
        assertEquals(patient.householdNumber, body.getString("household_number"))
        assertEquals(patient.zone, body.getString("zone"))
        assertEquals(patient.villageNumber, body.getString("village_number"))
        assertEquals(patient.isArchived, body.getBoolean("is_archived"))
        assertEquals(patient.allergy, body.getString("allergy"))

        server.shutdown()
    }

    @Test
    fun postMedicalRecord_drugRecord_sendsDrugHistoryAsInformation() {
        val (api, server) = restApiCapturing()
        val patient = sampleEditablePatient()

        val result = runBlocking { api.postMedicalRecord(patient, isDrugRecord = true, Protocol.HTTP) }
        check(result is NetworkResult.Success) { "got $result" }

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/patients/${patient.id}/medical_records", request.path)

        val body = JSONObject(request.body.readString(Charsets.UTF_8))
        assertEquals(patient.id, body.getString("patient_id"))
        assertTrue(body.getBoolean("is_drug_record"))
        assertEquals(patient.drugHistory, body.getString("information"))

        server.shutdown()
    }

    @Test
    fun putPregnancy_sendsStartAndEndDateForEndingPregnancy() {
        val (api, server) = restApiCapturing("""{"id": 77, "patientId": "123456"}""")
        val patient = sampleEditablePatient().apply {
            prevPregnancyEndDate = 1620000000
            prevPregnancyOutcome = "Live birth"
        }
        val startDate = BigInteger.valueOf(1596091500)

        val result = runBlocking { api.putPregnancy(patient, startDate, Protocol.HTTP) }
        check(result is NetworkResult.Success) { "got $result" }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/pregnancies/${patient.pregnancyId}", request.path)

        val body = JSONObject(request.body.readString(Charsets.UTF_8))
        assertEquals(patient.pregnancyId, body.getInt("id"))
        assertEquals(patient.id, body.getString("patient_id"))
        assertEquals(startDate.toString(), body.get("start_date").toString())
        assertEquals(patient.prevPregnancyEndDate, body.getLong("end_date"))
        assertEquals(patient.prevPregnancyOutcome, body.getString("outcome"))

        server.shutdown()
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