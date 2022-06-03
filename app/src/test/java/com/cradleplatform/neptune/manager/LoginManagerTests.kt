package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.model.CommonPatientReadingJsons
import com.cradleplatform.neptune.model.CommonReadingJsons
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.testutils.MockDependencyUtils
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import com.cradleplatform.neptune.utilities.SharedPreferencesMigration
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.math.BigInteger
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
internal class LoginManagerTests {
    private val testMainDispatcher = TestCoroutineDispatcher()

    companion object {

        private const val HEALTH_FACILITY_JSON =
            """
[
    {
        "location": "Sample Location",
        "facilityType": "HOSPITAL",
        "about": "Sample health centre",
        "healthFacilityPhoneNumber": "555-555-55555",
        "healthFacilityName": "H0000"
    },
    {
        "location": "District 1",
        "facilityType": "HCF_2",
        "about": "Has minimal resources",
        "healthFacilityPhoneNumber": "+256-413-837484",
        "healthFacilityName": "H1233"
    },
    {
        "location": "District 2",
        "facilityType": "HCF_3",
        "about": "Can do full checkup",
        "healthFacilityPhoneNumber": "+256-223-927484",
        "healthFacilityName": "H2555"
    },
    {
        "location": "District 3",
        "facilityType": "HCF_4",
        "about": "Has specialized equipment",
        "healthFacilityPhoneNumber": "+256-245-748573",
        "healthFacilityName": "H3445"
    },
    {
        "location": "District 4",
        "facilityType": "HOSPITAL",
        "about": "Urgent requests only",
        "healthFacilityPhoneNumber": "+256-847-0947584",
        "healthFacilityName": "H5123"
    }
]
    """

        private const val TEST_USER_FACILITY_NAME = "H3445"
        private const val TEST_FIRST_NAME = "NAME PERSON"
        private const val TEST_USER_ID = 4
        private const val TEST_USER_EMAIL = "test-android@example.com"
        private const val TEST_USER_PASSWORD = "password"
        private const val TEST_AUTH_TOKEN = "sOmEaUtHToken"
        private val TEST_USER_ROLE = UserRole.VHT
    }

    private val fakePatientDatabase: MutableList<Patient>
    private val fakeReadingDatabase: MutableList<Reading>
    private val mockDatabase: CradleDatabase
    private val mockPatientDao: PatientDao
    private val mockReadingDao: ReadingDao
    private val mockReferralDao: ReferralDao
    private val mockAssessmentDao: AssessmentDao
    private val fakeSharedPreferences: MutableMap<String, Any?>
    private val mockSharedPrefs: SharedPreferences
    private val fakeHealthFacilityDatabase: MutableList<HealthFacility>

    init {
        val (map, sharedPrefs) = MockDependencyUtils.createMockSharedPreferences()
        fakeSharedPreferences = map
        mockSharedPrefs = sharedPrefs

        val mockDatabaseStuff = MockDependencyUtils.createMockedDatabaseDependencies()
        mockDatabaseStuff.run {
            mockDatabase = mockedDatabase
            mockPatientDao = mockedPatientDao
            mockReadingDao = mockedReadingDao
            mockReferralDao = mockedReferralDao
            mockAssessmentDao = mockedAssessmentDao
            fakePatientDatabase = underlyingPatientDatabase
            fakeReadingDatabase = underlyingReadingDatabase
            fakeHealthFacilityDatabase = underlyingHealthFacilityDatabase
        }
    }

    private val mockRestApi: RestApi
    private val mockWebServer: MockWebServer
    private var failTheLoginWithIOIssues = false
    init {
        val (api, server) = MockWebServerUtils.createRestApiWithMockedServer(mockSharedPrefs) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{
                    when (request.path) {
                        "/api/user/auth" -> {
                            try {
                                val userLogin =
                                    JSONObject(request.body.readString(Charset.defaultCharset()))
                                if (userLogin.getString("email") != TEST_USER_EMAIL) {
                                    setResponseCode(401)
                                    return@response
                                }

                                if (userLogin.getString("password") != TEST_USER_PASSWORD) {
                                    setResponseCode(401)
                                    return@response
                                }
                            } catch (e: JSONException) {
                                setResponseCode(500)
                                return@response
                            }

                            val json = JacksonMapper.createWriter<LoginResponse>()
                                .writeValueAsString(
                                    LoginResponse(
                                        token = TEST_AUTH_TOKEN,
                                        email = TEST_USER_EMAIL,
                                        role = "VHT",
                                        userId = TEST_USER_ID,
                                        firstName = TEST_FIRST_NAME,
                                        healthFacilityName = TEST_USER_FACILITY_NAME
                                    )
                                )
                            setResponseCode(200)
                            setBody(json)
                        }
                        "/api/mobile/patients" -> {
                            if (request.headers["Authorization"] != "Bearer $TEST_AUTH_TOKEN") {
                                setResponseCode(403)
                                setBody(request.headers.toString())
                                return@response
                            }

                            val json = CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
                            if (!failTheLoginWithIOIssues) {
                                setResponseCode(200)
                                setBody(json)
                            } else {
                                // mess it up somehow
                                setResponseCode(200)
                                setBody("${json.substring(0, json.length - 6)}ThisMessesItUp")
                            }
                        }
                        "/api/mobile/readings" -> {
                            if (request.headers["Authorization"] != "Bearer $TEST_AUTH_TOKEN") {
                                setResponseCode(403)
                                setBody(request.headers.toString())
                                return@response
                            }

                            val json = CommonReadingJsons.allReadingsJsonExpectedPair.first
                            if (!failTheLoginWithIOIssues) {
                                setResponseCode(200)
                                setBody(json)
                            } else {
                                // mess it up somehow
                                setResponseCode(200)
                                setBody("${json.substring(0, json.length - 6)}ThisMessesItUp")
                            }
                        }
                        "/api/facilities" -> {
                            if (request.headers["Authorization"] != "Bearer $TEST_AUTH_TOKEN") {
                                setResponseCode(403)
                                return@response
                            }

                            val json = HEALTH_FACILITY_JSON
                            if (!failTheLoginWithIOIssues) {
                                setResponseCode(200)
                                setBody(json)
                            } else {
                                // mess it up somehow. server says it's okay but something happened
                                // during download
                                setResponseCode(200)
                                setBody("${json.substring(0, json.length - 6)}ThisMessesItUp")
                            }
                        }
                        else -> setResponseCode(404)
                    }

                }
            }
        }
        mockRestApi = api
        mockWebServer = server
    }

    // Mock Managers that only being used to initialize LoginManager
    private val mockHealthManager = HealthFacilityManager(mockDatabase)
    private val fakePatientManager = PatientManager(mockDatabase, mockPatientDao, mockReadingDao, mockRestApi)
    private val fakeReadingManager = ReadingManager(mockDatabase, mockReadingDao, mockRestApi)
    private val mockReferralManager = ReferralManager(mockDatabase, mockReferralDao, mockRestApi)
    private val mockAssessmentManager = AssessmentManager(mockDatabase, mockAssessmentDao, mockRestApi)

    private val mockContext = mockk<Context>(relaxed = true) {
        every { getString(R.string.key_vht_name) } returns "setting_vht_name"
        every { getString(R.string.key_role) } returns "setting_role"
    }

    // Variable Storing an instance of the loginManger being tested
    private lateinit var loginManager :LoginManager

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testMainDispatcher)
        fakeSharedPreferences.clear()
        fakeHealthFacilityDatabase.clear()
        fakePatientDatabase.clear()
        fakeReadingDatabase.clear()

        loginManager = LoginManager(
            mockRestApi,
            mockSharedPrefs,
            mockDatabase,
            mockContext
        )
    }
    @AfterEach
    fun cleanUp() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    @ExperimentalTime
    @Test
    fun `login with right credentials and logout`() {
        failTheLoginWithIOIssues = false

        runBlocking {

            val result = withTimeout(Duration.seconds(10)) {
                loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD)
            }
            assert(result is NetworkResult.Success) {
                "expected login to be successful, but it failed, with " +
                    "result $result and\n" +
                    "shared prefs map $fakeSharedPreferences"
            }

            val latestVersion = SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
            val versionStored = fakeSharedPreferences[
                SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION] as? Int
            assertEquals(latestVersion, versionStored) {
                "expected stored shared pref version to be $latestVersion, but got $versionStored" +
                    "make sure that the login stores the SharedPreferences version, otherwise " +
                    "migrations in the future can fail"
            }

            assert(loginManager.isLoggedIn())
            assertEquals(TEST_AUTH_TOKEN, fakeSharedPreferences["token"]) { "bad auth token" }
            assertEquals(TEST_USER_ID, fakeSharedPreferences["userId"]) { "bad userId" }
            assertEquals(
                TEST_FIRST_NAME,
                fakeSharedPreferences[mockContext.getString(R.string.key_vht_name)]
            ) { "bad first name" }

            val role = UserRole.safeValueOf(fakeSharedPreferences[mockContext.getString(R.string.key_role)] as String)

            assertEquals(TEST_USER_ROLE, role) { "unexpected role $role; expected $TEST_USER_ROLE" }
/*
            TODO: move this assertion section to test for SyncWorker (refer to issue #23)

            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_PATIENT_SYNC)) {
                "missing last patient sync time; shared pref dump: $fakeSharedPreferences"
            }
            assert(BigInteger(fakeSharedPreferences[SyncWorker.LAST_PATIENT_SYNC] as String).toLong() > 100L) {
                "last patient sync time too small"
            }
            assert(fakeSharedPreferences.containsKey(SyncWorker.LAST_READING_SYNC)) {
                "missing last reading sync time"
            }
            assert(BigInteger(fakeSharedPreferences[SyncWorker.LAST_READING_SYNC]!! as String).toLong() > 100L) {
                "last reading sync time too small"
            }
            assertNotEquals(0, fakeHealthFacilityDatabase.size) {
                "LoginManager failed to do health facility download; dumping facility db: $fakeHealthFacilityDatabase"
            }

            val userSelectedHealthFacilities = fakeHealthFacilityDatabase
                .filter { it.isUserSelected }
            assertNotEquals(0, userSelectedHealthFacilities.size) {
                "LoginManager failed to select a health facility; dumping facility db: $fakeHealthFacilityDatabase"
            }
            assertEquals(1, userSelectedHealthFacilities.size) {
                "LoginManager selected too many health health facilities"
            }
            assertEquals(TEST_USER_FACILITY_NAME, userSelectedHealthFacilities[0].name) {
                "wrong health facility selected"
            }

 */
            loginManager.logout()

            assert(fakeSharedPreferences.isEmpty())
            assert(fakePatientDatabase.isEmpty())
            assert(fakeReadingDatabase.isEmpty())
            assert(fakeHealthFacilityDatabase.isEmpty())
        }
    }

    @Test
    fun `login with right credentials, IO error during download, nothing should be added`() {
        failTheLoginWithIOIssues = true
        mockDatabase.clearAllTables()

        runBlocking {
            val result = loginManager.login(
                TEST_USER_EMAIL,
                TEST_USER_PASSWORD,
            )
            // Note: we say success, but this just lets us move on from the LoginActivity.
            // TODO: Need to communicate failure.
            assert(result is NetworkResult.Success) {
                "expected a Success, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }

            // Should be logged in, but the download of patients and facilities failed
            assert(loginManager.isLoggedIn())

            // withTransaction should make it so that the changes are not committed.
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }

            assert(!fakeSharedPreferences.containsKey(SyncWorker.LAST_PATIENT_SYNC)) {
                "sync time should not be stored for a failed, incomplete download; otherwise," +
                    "the user will no longer be able to sync"
            }
        }
    }

    @Test
    fun `login with wrong credentials`() {
        failTheLoginWithIOIssues = false
        mockDatabase.clearAllTables()

        runBlocking {
            val result = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD + "_invalid_version")
            assert(result is NetworkResult.Failure) {
                "expected a failure, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }
            val statusCode = (result as NetworkResult.Failure).statusCode
            assertEquals(401, statusCode) { "expected 401 status code, but got $statusCode" }

            assert(!loginManager.isLoggedIn())
            assertEquals(0, fakeSharedPreferences.keys.size)
            val healthFacilities = fakeHealthFacilityDatabase
            assertEquals(0, healthFacilities.size) { "nothing should be added" }
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }
        }
    }
}
