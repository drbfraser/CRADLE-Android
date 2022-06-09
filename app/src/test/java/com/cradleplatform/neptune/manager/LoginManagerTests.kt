package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
internal class LoginManagerTests {
    private val testMainDispatcher = TestCoroutineDispatcher()

    companion object {
        private const val TEST_USER_FACILITY_NAME = "H3445"
        private const val TEST_FIRST_NAME = "NAME PERSON"
        private const val TEST_USER_ID = 4
        private const val TEST_USER_EMAIL = "test-android@example.com"
        private const val TEST_USER_PASSWORD = "password"
        private const val TEST_AUTH_TOKEN = "sOmEaUtHToken"
        private val TEST_USER_ROLE = UserRole.VHT
    }

    private val fakeSharedPreferences: MutableMap<String, Any?>
    private val mockSharedPrefs: SharedPreferences

    init {
        val (map, sharedPrefs) = MockDependencyUtils.createMockSharedPreferences()
        fakeSharedPreferences = map
        mockSharedPrefs = sharedPrefs
    }

    private val mockRestApi: RestApi
    private val mockWebServer: MockWebServer

    //private var failTheLoginWithIOIssues = false
    init {
        val (api, server) = MockWebServerUtils.createRestApiWithServerBlock(mockSharedPrefs) {
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
                        else -> setResponseCode(404)
                    }

                }
            }
        }
        mockRestApi = api
        mockWebServer = server
    }

    private val mockContext = mockk<Context>(relaxed = true) {
        every { getString(R.string.key_vht_name) } returns "setting_vht_name"
        every { getString(R.string.key_role) } returns "setting_role"
    }

    private var databaseCleared: Boolean = false
    private val studDatabase: CradleDatabase = mockk(relaxed = true) {
        every { clearAllTables() } answers {
            databaseCleared = true
        }
    }

    // Variable Storing an instance of the loginManger being tested
    private lateinit var loginManager: LoginManager

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // reset values
        Dispatchers.setMain(testMainDispatcher)
        fakeSharedPreferences.clear()
        databaseCleared = false

        loginManager = LoginManager(
            mockRestApi,
            mockSharedPrefs,
            studDatabase,
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

            val role =
                UserRole.safeValueOf(fakeSharedPreferences[mockContext.getString(R.string.key_role)] as String)

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
            assert(!databaseCleared)
            // logout
            loginManager.logout()
            assert(databaseCleared)
            assert(fakeSharedPreferences.isEmpty())
        }
    }

    @Test
    fun `login with wrong password`() {
        runBlocking {
            val result =
                loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD + "_invalid_version")
            assert(result is NetworkResult.Failure) {
                "expected a failure, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }
            val statusCode = (result as NetworkResult.Failure).statusCode
            assertEquals(401, statusCode) { "expected 401 status code, but got $statusCode" }

            assert(!loginManager.isLoggedIn())
            assertEquals(0, fakeSharedPreferences.keys.size)

            /*
            val healthFacilities = fakeHealthFacilityDatabase
            assertEquals(0, healthFacilities.size) { "nothing should be added" }
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }
             */
        }
    }

    @Test
    fun `login with wrong email`() {
        runBlocking {
            val result =
                loginManager.login(TEST_USER_EMAIL + "_invalid_version", TEST_USER_PASSWORD)
            assert(result is NetworkResult.Failure) {
                "expected a failure, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }
            val statusCode = (result as NetworkResult.Failure).statusCode
            assertEquals(401, statusCode) { "expected 401 status code, but got $statusCode" }

            assert(!loginManager.isLoggedIn())
            assertEquals(0, fakeSharedPreferences.keys.size)
        }
    }

    @Test
    fun `login a second time`() {
    }

    @Test
    fun `logout with host and port persists in sharedPreference`() {

    }

}
