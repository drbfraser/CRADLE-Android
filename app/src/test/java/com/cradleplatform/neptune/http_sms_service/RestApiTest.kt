package com.cradleplatform.neptune.http_sms_service

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.ReadingSyncResult
import com.cradleplatform.neptune.http_sms_service.http.ReferralSyncResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.http.SyncError
import com.cradleplatform.neptune.manager.LoginResponse
import com.cradleplatform.neptune.manager.LoginResponseUser
import com.cradleplatform.neptune.manager.SmsKey
import com.cradleplatform.neptune.model.CommonReadingJsons
import com.cradleplatform.neptune.model.CommonReferralJsons
import com.cradleplatform.neptune.model.GestationalAgeWeeks
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import com.cradleplatform.neptune.utilities.Protocol
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@ExperimentalCoroutinesApi
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
        // Reading sync logs on the main dispatcher, which doesn't exist in unit tests.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
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

    private fun restApiResponding(
        responseCode: Int,
        responseBody: String
    ): Pair<RestApi, MockWebServer> =
        MockWebServerUtils.createRestApiWithServerBlock {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    MockResponse().setResponseCode(responseCode).setBody(responseBody)
            }
        }

    /** Runs a reading sync against [api], returning its result and the readings it downloaded. */
    private fun syncReadings(api: RestApi): Pair<ReadingSyncResult, List<Reading>> = runBlocking {
        val channel = Channel<Reading>()
        val downloaded = async { channel.toList() }
        val result = api.syncReadings(
            readingsToUpload = emptyList(),
            readingChannel = channel,
            protocol = Protocol.HTTP
        ) { _, _ -> }
        result to downloaded.await()
    }

    /** Runs a referral sync against [api], returning its result and the referrals it downloaded. */
    private fun syncReferrals(api: RestApi): Pair<ReferralSyncResult, List<Referral>> = runBlocking {
        val channel = Channel<Referral>()
        val downloaded = async { channel.toList() }
        val result = api.syncReferrals(
            referralsToUpload = emptyList(),
            referralChannel = channel,
            protocol = Protocol.HTTP
        ) { _, _ -> }
        result to downloaded.await()
    }

    @Test
    fun syncReadings_errorsBeforeReadings_downloadsReadingsAndReturnsErrors() {
        val (readingsJson, expectedReadings) = CommonReadingJsons.allReadingsJsonExpectedPair
        val (api, server) = restApiResponding(
            207,
            """
            {
                "errors": [
                    {"readingId": "abc-123", "errors": "Not authorized to sync this patient."},
                    {"readingId": "def-456", "errors": "Patient does not exist."}
                ],
                "readings": $readingsJson
            }
            """.trimIndent()
        )

        val (result, readings) = syncReadings(api)

        check(result.networkResult is NetworkResult.Success) { "got ${result.networkResult}" }
        assertEquals(expectedReadings.map { it.id }, readings.map { it.id })
        assertEquals(expectedReadings.size, result.totalReadingsDownloaded)
        assertEquals(
            listOf(
                SyncError("abc-123", "Not authorized to sync this patient."),
                SyncError("def-456", "Patient does not exist.")
            ),
            result.errors
        )

        server.shutdown()
    }

    @Test
    fun syncReadings_noErrorsField_downloadsReadingsWithNoErrors() {
        val (readingsJson, expectedReadings) = CommonReadingJsons.allReadingsJsonExpectedPair
        val (api, server) = restApiResponding(200, """{"readings": $readingsJson}""")

        val (result, readings) = syncReadings(api)

        check(result.networkResult is NetworkResult.Success) { "got ${result.networkResult}" }
        assertEquals(expectedReadings.map { it.id }, readings.map { it.id })
        assertEquals(emptyList<SyncError>(), result.errors)

        server.shutdown()
    }

    @Test
    fun syncReferrals_errorsBeforeReferrals_downloadsReferralsAndReturnsErrors() {
        val (referralsJson, expectedReferrals) = CommonReferralJsons.allReferralsJsonExpectedPair
        val (api, server) = restApiResponding(
            207,
            """
            {
                "errors": [
                    {"referralId": "abc-123", "errors": "Not authorized to sync this patient."}
                ],
                "referrals": $referralsJson
            }
            """.trimIndent()
        )

        val (result, referrals) = syncReferrals(api)

        check(result.networkResult is NetworkResult.Success) { "got ${result.networkResult}" }
        assertEquals(expectedReferrals.map { it.id }, referrals.map { it.id })
        assertEquals(
            listOf(SyncError("abc-123", "Not authorized to sync this patient.")),
            result.errors
        )

        server.shutdown()
    }

    @Test
    fun syncReferrals_noErrorsField_downloadsReferralsWithNoErrors() {
        val (referralsJson, expectedReferrals) = CommonReferralJsons.allReferralsJsonExpectedPair
        val (api, server) = restApiResponding(200, """{"referrals": $referralsJson}""")

        val (result, referrals) = syncReferrals(api)

        check(result.networkResult is NetworkResult.Success) { "got ${result.networkResult}" }
        assertEquals(expectedReferrals.map { it.id }, referrals.map { it.id })
        assertEquals(emptyList<SyncError>(), result.errors)

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