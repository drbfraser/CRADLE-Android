package com.cradleplatform.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.UserRole
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.sync.PeriodicSyncer
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
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
internal class LoginManagerTests {
    private val testMainDispatcher = TestCoroutineDispatcher()

    companion object {
        private const val TEST_USER_FACILITY_NAME = "H3445"
        private const val TEST_NAME = "NAME PERSON"
        private const val TEST_USER_ID = 4
        private const val TEST_USER_EMAIL = "test-android@example.com"
        private const val TEST_USERNAME = "test-android"
        private const val TEST_SMS_KEY = "SGVsbG8sIFdvcmxkIQ"
        private val TEST_USER_PHONE_NUMBERS = listOf<String>("+1-666-666-6666", "+1-777-777-7777", "+1-555-555-5555")
        private const val TEST_USER_PASSWORD = "password"
        private const val TEST_ACCESS_TOKEN = "sOmEaUtHToken"
        private val TEST_USER_ROLE = UserRole.VHT
    }

    private val fakeSharedPreferences: MutableMap<String, Any?>
    private val mockSharedPrefs: SharedPreferences
    private val fakeEncryptedSharedPreferences: MutableMap<String, Any?>
    private val encryptedSharedPreferences: EncryptedSharedPreferences

    init {
        val (map, sharedPrefs) = MockDependencyUtils.createMockSharedPreferences()
        fakeSharedPreferences = map
        mockSharedPrefs = sharedPrefs
        val (map2, encrSharedPrefs) = MockDependencyUtils.createMockEncryptedSharedPreferences()
        fakeEncryptedSharedPreferences = map2
        encryptedSharedPreferences = encrSharedPrefs
    }

    private val mockRestApi: RestApi
    private val mockWebServer: MockWebServer

    init {
        val (api, server) = MockWebServerUtils.createRestApiWithServerBlock(mockSharedPrefs) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{
                    when (request.path) {
                        "/api/user/auth" -> {
                            try {
                                val userLogin =
                                    JSONObject(request.body.readString(Charset.defaultCharset()))
                                if (userLogin.getString("username") != TEST_USER_EMAIL) {
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
                                        accessToken = TEST_ACCESS_TOKEN,
                                        user = LoginResponseUser(
                                            id = TEST_USER_ID,
                                            email = TEST_USER_EMAIL,
                                            role = "VHT",
                                            name = TEST_NAME,
                                            healthFacilityName = TEST_USER_FACILITY_NAME,
                                            phoneNumbers = TEST_USER_PHONE_NUMBERS,
                                            username = TEST_USERNAME,
                                            smsKey = LoginResponseSmsKey(
                                                key = TEST_SMS_KEY
                                            )
                                        )
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
        every { getString(R.string.key_server_hostname) } returns "settings_server_hostname"
        every { getString(R.string.key_server_port) } returns "settings_server_port"
    }

    private var databaseCleared: Boolean = false
    private val studDatabase: CradleDatabase = mockk(relaxed = true) {
        every { clearAllTables() } answers {
            databaseCleared = true
        }
    }

    // Variable Storing an instance of the loginManger being tested
    private lateinit var loginManager: LoginManager
    @Mock
    private lateinit var mockPeriodicSyncer: PeriodicSyncer
    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this) // initiate all @Mock
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        // reset values
        Dispatchers.setMain(testMainDispatcher)
        fakeSharedPreferences.clear()
        databaseCleared = false

        // Mock the EncryptedSharedPreferences class
        mockkStatic(EncryptedSharedPreferences::class)

// Define what should be returned when EncryptedSharedPreferences.create is called
        every { EncryptedSharedPreferences.create(any<String>(), any<String>(), any<Context>(),
            any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
            any<EncryptedSharedPreferences.PrefValueEncryptionScheme>())
        } returns encryptedSharedPreferences

        loginManager = LoginManager(
            mockRestApi,
            mockSharedPrefs,
            studDatabase,
            mockPeriodicSyncer,
            SmsKeyManager(mockContext),
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

            val result = withTimeout((10).seconds) {
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
            assertEquals(TEST_ACCESS_TOKEN, fakeSharedPreferences["accessToken"]) { "bad access token" }
            assertEquals(TEST_USER_ID, fakeSharedPreferences["userId"]) { "bad userId" }
            assertEquals(
                TEST_NAME,
                fakeSharedPreferences[mockContext.getString(R.string.key_vht_name)]
            ) { "bad name" }

            val role =
                UserRole.safeValueOf(fakeSharedPreferences[mockContext.getString(R.string.key_role)] as String)

            assertEquals(TEST_USER_ROLE, role) { "unexpected role $role; expected $TEST_USER_ROLE" }

            assert(!databaseCleared) {
                "Database was already cleared before logging out"
            }
            // logout
            loginManager.logout()
            assert(databaseCleared) {
                "Database was not cleared after logging out"
            }
            assert(fakeSharedPreferences.isEmpty()) {
                "Shared Preferences was not cleared after logging out"
            }
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

            assert(!loginManager.isLoggedIn()){
                "Login should not be successful with invalid password"
            }
            assertEquals(0, fakeSharedPreferences.keys.size) {
                "SharedPreferences should be empty if login failed"
            }

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

            assert(!loginManager.isLoggedIn()){
                "Login should not be successful with invalid email"
            }
            assertEquals(0, fakeSharedPreferences.keys.size) {
                "SharedPreferences should be empty if login failed"
            }

        }
    }

    @Test
    fun `login a second time`() {
        runBlocking {

            val firstResult = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD)
            // light check if has login successfully
            assert(firstResult is NetworkResult.Success) {
                "Login should succeed for the first time"
            }
            assert(loginManager.isLoggedIn()) {
                "isLoggedIn() for loginManager should be true for the first time"
            }

            // login a second time
            val secondResult = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD)
            assert(secondResult is NetworkResult.NetworkException){
                "Expected a Network Exception \"already logged in\"," +
                    "but got result $secondResult"
            }

        }
    }

    @Test
    fun `logout with hostName and port persisting in sharedPreference`() {
        val testHostName = "10.0.2.2"
        val testHostPort = "5000"
        fakeSharedPreferences[mockContext.getString(R.string.key_server_hostname)] = testHostName
        fakeSharedPreferences[mockContext.getString(R.string.key_server_port)] = testHostPort

        runBlocking {
            val result = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD)
            // light check if has login successfully
            assert(result is NetworkResult.Success)
            assert(loginManager.isLoggedIn())

            assertEquals(TEST_ACCESS_TOKEN, fakeSharedPreferences["accessToken"]) { "Bad access token." }
            assertEquals(TEST_USER_ID, fakeSharedPreferences["userId"]) { "Bad userId." }
            assertEquals(
                TEST_NAME,
                fakeSharedPreferences[mockContext.getString(R.string.key_vht_name)]
            ) { "Bad name." }

            val role =
                UserRole.safeValueOf(fakeSharedPreferences[mockContext.getString(R.string.key_role)] as String)

            assertEquals(TEST_USER_ROLE, role) { "unexpected role $role; expected $TEST_USER_ROLE" }

            // logs out, both settings should persist
            loginManager.logout()
            assert(fakeSharedPreferences.isNotEmpty())
            assertEquals(fakeSharedPreferences[mockContext.getString(R.string.key_server_hostname)], testHostName)
            assertEquals(fakeSharedPreferences[mockContext.getString(R.string.key_server_port)], testHostPort)

        }
    }

}
