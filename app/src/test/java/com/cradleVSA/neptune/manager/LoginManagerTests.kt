package com.cradleVSA.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.room.withTransaction
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.model.CommonPatientReadingJsons
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.net.Success
import com.cradleVSA.neptune.sync.SyncStepper
import com.cradleVSA.neptune.utilitiles.SharedPreferencesMigration
import io.mockk.coEvery
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

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
    }

    private fun createMockRestApi(streamingWillSucceed: Boolean = true) = mockk<RestApi> {
        coEvery {
            authenticate(any(), any())
        } answers {
            val email = arg<String>(0)
            val password = arg<String>(1)
            if (email != TEST_USER_EMAIL) {
                // Let's say no other user exists
                return@answers Failure(ByteArray(1), 401)
            }
            // Trying to login as TEST_USER_EMAIL now.
            if (password != TEST_USER_PASSWORD) {
                return@answers Failure(ByteArray(1), 401)
            }

            val response = LoginResponse(
                token = TEST_AUTH_TOKEN,
                email = TEST_USER_EMAIL,
                roles = arrayOf("VHT"),
                userId = TEST_USER_ID,
                firstName = TEST_FIRST_NAME,
                healthFacilityName = TEST_USER_FACILITY_NAME
            )

            Success(response, 200)
        }

        coEvery {
            getAllHealthFacilities(any())
        } coAnswers {
            // Simulate the network sending over the JSON as a stream of bytes.
            val block = firstArg<suspend (InputStream) -> Unit>()
            val jsonStream = HEALTH_FACILITY_JSON.byteInputStream()
            block(jsonStream)
            if (streamingWillSucceed) {
                Success(Unit, 200)
            } else {
                Failure(ByteArray(0), 500)
            }
        }

        coEvery {
            getAllPatientsStreaming(any())
        } coAnswers {
            // Simulate the network sending over the JSON as a stream of bytes.
            val block = firstArg<suspend (InputStream) -> Unit>()
            val json = CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
            val jsonStream = if (streamingWillSucceed) {
                json.byteInputStream()
            } else {
                "${json.substring(0, json.length / 2)} a}})".byteInputStream()
            }

            // Simulate Http class catching IOExceptions
            try {
                block(jsonStream)
            } catch (e: IOException) {
            }

            if (streamingWillSucceed) {
                Success(Unit, 200)
            } else {
                Failure(ByteArray(0), 500)
            }
        }
    }

    private val fakeSharedPreferences = mutableMapOf<String, Any?>()
    private val fakeHealthFacilityDatabase = mutableListOf<HealthFacility>()
    private val fakePatientDatabase = mutableListOf<Patient>()
    private val fakeReadingDatabase = mutableListOf<Reading>()

    private var isMockkStaticDone = false
    private val lock = ReentrantLock()

    private fun createMockDatabase() = lock.withLock{
        mockk<CradleDatabase> {
            // https://stackoverflow.com/a/56652529 - if we don't do this, test will hang forever
            if (!isMockkStaticDone) {
                mockkStatic("androidx.room.RoomDatabaseKt")
                isMockkStaticDone = true
            }

            // FIXME: With multiple coroutines using withTransaction in LoginManager, there
            //  will be suspend lambdas that don't get called at all, because when a lambda is
            //  captured, it overrides any lambdas that was there before. To work around this,
            //  we don't test parallel downloads in LoginManager.
            coEvery { withTransaction(captureLambda<suspend () -> Unit>()) } coAnswers {
                var isTransactionSuccessful = false
                try {
                    lambda<suspend () -> Unit>().captured.invoke()
                    isTransactionSuccessful = true
                } finally {
                    if (!isTransactionSuccessful) {
                        // Simulate rollback
                        fakeHealthFacilityDatabase.clear()
                        fakePatientDatabase.clear()
                        fakeReadingDatabase.clear()
                    }
                }
            }

            every { clearAllTables() } answers {
                fakeHealthFacilityDatabase.clear()
                fakePatientDatabase.clear()
                fakeReadingDatabase.clear()
            }
            every { close() } returns Unit
        }
    }

    private val mockPatientDao = mockk<PatientDao> {
        coEvery { insert(any()) } answers {
            fakePatientDatabase.add(firstArg())
            5L
        }
    }
    private val mockReadingDao = mockk<ReadingDao> {
        coEvery { insertAll(any()) } answers {
            val readings = firstArg<List<Reading>>()
            readings.forEach { reading ->
                fakePatientDatabase.find { it.id == reading.patientId }
                    ?: error(
                        "foreign key constraint: trying to insert reading for nonexistent patient" +
                            "; patientId ${reading.patientId} not in fake database.\n" +
                            "reading=$reading\n" +
                            "fakePatientDatabase=$fakePatientDatabase\n" +
                            "fakeReadingDatabase=$fakeReadingDatabase"
                    )
            }
            fakeReadingDatabase.addAll(readings)
        }
        coEvery { insert(any()) } answers {
            val reading: Reading = firstArg()
            fakePatientDatabase.find { it.id == reading.patientId }
                ?: error(
                    "foreign key constraint: trying to insert reading for nonexistent patient; " +
                        "patientId ${reading.patientId} not in fake database.\n" +
                        "reading=$reading\n" +
                        "fakePatientDatabase=$fakePatientDatabase\n" +
                        "fakeReadingDatabase=$fakeReadingDatabase"
                )
            fakeReadingDatabase.add(reading)
        }
    }

    private fun <T> putInFakeSharedPreference(key: String?, value: T?) {
        key ?: return
        fakeSharedPreferences[key] = value
    }

    private val mockSharedPrefs = mockk<SharedPreferences> {
        every { edit() } returns mockk editor@{
            every { putString(any(), any()) } answers {
                putInFakeSharedPreference<String?>(firstArg(), secondArg())
                this@editor
            }

            every { getString(any(), any()) } answers {
                val stringValue = fakeSharedPreferences[firstArg()] as String?
                if (stringValue == null && !fakeSharedPreferences.contains(firstArg())) {
                    secondArg()
                } else {
                    stringValue
                }
            }

            every { putInt(any(), any()) } answers {
                putInFakeSharedPreference<Int?>(firstArg(), secondArg())
                this@editor
            }

            every { putLong(any(), any()) } answers {
                putInFakeSharedPreference<Long?>(firstArg(), secondArg())
                this@editor
            }

            every { contains(any()) } answers { fakeSharedPreferences.containsKey(firstArg()) }

            every { commit() } returns true
            every { apply() } returns Unit
            every { clear() } answers {
                fakeSharedPreferences.clear()
                this@editor
            }
        }
    }

    private val fakePatientManager = PatientManager(
        createMockDatabase(),
        mockPatientDao,
        mockReadingDao,
        createMockRestApi()
    )

    private val mockHealthManager = mockk<HealthFacilityManager> {
        coEvery {
            addAll(any())
        } answers {
            val healthFacilities: List<HealthFacility> = arg(0) ?: return@answers
            healthFacilities.forEach { facilityToBeAdded ->
                fakeHealthFacilityDatabase.find { it.id == facilityToBeAdded.id }.let {
                    fakeHealthFacilityDatabase.remove(it)
                }
            }
            fakeHealthFacilityDatabase.addAll(healthFacilities)
        }
        coEvery {
            add(any())
        } answers {
            val healthFacility = firstArg<HealthFacility>()
            // Replace
            fakeHealthFacilityDatabase.find { it.id == healthFacility.id }.let {
                fakeHealthFacilityDatabase.remove(it)
            }
            fakeHealthFacilityDatabase.add(firstArg())
        }
    }
    private val mockContext = mockk<Context>(relaxed = true) {
        every { getString(R.string.key_vht_name) } returns "firstName"
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testMainDispatcher)
        fakeSharedPreferences.clear()
        fakeHealthFacilityDatabase.clear()
        fakePatientDatabase.clear()
        fakeReadingDatabase.clear()
    }
    @AfterEach
    fun cleanUp() {
        Dispatchers.resetMain()
    }

    @ExperimentalTime
    @Test
    fun `login with right credentials and logout`() {
        runBlocking {
            val loginManager = LoginManager(
                createMockRestApi(),
                mockSharedPrefs,
                createMockDatabase(),
                fakePatientManager,
                mockHealthManager,
                mockContext
            )

            val result = withTimeout(10.seconds) {
                loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD, parallelDownload = false)
            }
            assert(result is Success) {
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

            assert(fakeSharedPreferences.containsKey(SyncStepper.LAST_PATIENT_SYNC)) {
                "missing last patient sync time"
            }
            assert(fakeSharedPreferences[SyncStepper.LAST_PATIENT_SYNC]!! as Long > 100L) {
                "last patient sync time too small"
            }
            assert(fakeSharedPreferences.containsKey(SyncStepper.LAST_READING_SYNC)) {
                "missing last reading sync time"
            }
            assert(fakeSharedPreferences[SyncStepper.LAST_READING_SYNC]!! as Long > 100L) {
                "last reading sync time too small"
            }

            val userSelectedHealthFacilities = fakeHealthFacilityDatabase
                .filter { it.isUserSelected }
            assertNotEquals(0, userSelectedHealthFacilities.size) {
                "LoginManager failed to select a health facility"
            }
            assertEquals(1, userSelectedHealthFacilities.size) {
                "LoginManager selected too many health health facilities"
            }
            assertEquals(TEST_USER_FACILITY_NAME, userSelectedHealthFacilities[0].name) {
                "wrong health facility selected"
            }

            assertEquals(
                CommonPatientReadingJsons.allPatientsJsonExpectedPair.second.size,
                fakePatientDatabase.size
            ) {
                "parsing the patients failed: not enough patients parsed"
            }

            fakeReadingDatabase.forEach {
                assert(it.isUploadedToServer)
            }
            fakePatientDatabase.forEach {
                assert(it.base != null)
            }

            // Verify that the streamed parsing via Jackson of the simulated downloading of all
            // patients and their readings from the server was correct.
            val expectedPatientAndReadings = CommonPatientReadingJsons
                .allPatientsJsonExpectedPair.second
            expectedPatientAndReadings.forEach { patientAndReadings ->
                val (expectedPatient, expectedReadings) =
                    patientAndReadings.patient to patientAndReadings.readings.map {
                        it.copy(isUploadedToServer = true)
                    }

                // The patient ID must have been parsed correctly at least since that's the
                // primary key. When we find a match, we then check to see if all of the fields
                // are the same. Doing a direct equality check gives us less information about
                // what failed.
                fakePatientDatabase.find { it.id == expectedPatient.id }
                    ?.let { parsedPatient ->
                        assertEquals(expectedPatient, parsedPatient) {
                            "found a patient with the same ID, but one or more properties are wrong"
                        }
                    }
                    ?: fail { "couldn't find expected patient in fake database: $expectedPatient" }

                expectedReadings.forEach { expectedReading ->
                    fakeReadingDatabase.find { it.id == expectedReading.id }
                        ?.let { parsedReading ->
                            assertEquals(expectedReading, parsedReading) {
                                "found a reading with the same ID, but one or more properties are" +
                                    " wrong"
                            }
                        }
                        ?: fail { "couldn't find expected reading in fake database: $expectedReading" }
                }
            }

            loginManager.logout()

            assert(fakeSharedPreferences.isEmpty())
            assert(fakePatientDatabase.isEmpty())
            assert(fakeReadingDatabase.isEmpty())
            assert(fakeHealthFacilityDatabase.isEmpty())
        }
    }

    @Test
    fun `login with right credentials, server 500 error during download, nothing should be added`() {
        runBlocking {
            val loginManager = LoginManager(
                createMockRestApi(streamingWillSucceed = false),
                mockSharedPrefs,
                createMockDatabase(),
                fakePatientManager,
                mockHealthManager,
                mockContext
            )

            val result = loginManager.login(
                TEST_USER_EMAIL,
                TEST_USER_PASSWORD,
                parallelDownload = false
            )
            // Note: we say success, but this just lets us move on from the LoginActivity.
            // TODO: Need to communicate failure.
            assert(result is Success) {
                "expected a Success, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }

            // Should be logged in, but the download of patients and facilities failed
            assert(loginManager.isLoggedIn())

            // withTransaction should make it so that the changes are not committed.
            assertEquals(0, fakeHealthFacilityDatabase.size) { "nothing should be added" }
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }

            assert(!fakeSharedPreferences.containsKey(SyncStepper.LAST_PATIENT_SYNC)) {
                "sync time should not be stored for a failed, incomplete download; otherwise," +
                    "the user will no longer be able to sync"
            }
        }
    }

    @Test
    fun `login with wrong credentials`() {
        runBlocking {
            val loginManager = LoginManager(
                createMockRestApi(),
                mockSharedPrefs,
                createMockDatabase(),
                fakePatientManager,
                mockHealthManager,
                mockContext
            )

            val result = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD + "wronglol")
            assert(result is Failure) {
                "expected a failure, but got result $result and " +
                    "shared prefs map $fakeSharedPreferences"
            }
            val statusCode = (result as Failure).statusCode
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
