package com.cradle.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.R
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.daos.PatientDao
import com.cradle.neptune.database.daos.ReadingDao
import com.cradle.neptune.ext.map
import com.cradle.neptune.model.CommonPatientReadingJsons
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import com.cradle.neptune.utilitiles.SharedPreferencesMigration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.InputStream

@ExperimentalCoroutinesApi
internal class LoginManagerTests {
    private val testMainDispatcher = TestCoroutineDispatcher()
    private val testTransactionExecutor = TestCoroutineDispatcher()

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

    private val mockRestApi = mockk<RestApi> {
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

            val json = JSONObject().apply {
                put("token", TEST_AUTH_TOKEN)
                put("userId", TEST_USER_ID)
                put(mockContext.getString(R.string.key_vht_name), TEST_FIRST_NAME)
                put("healthFacilityName", TEST_USER_FACILITY_NAME)
            }

            Success(json, 200)
        }

        coEvery {
            getAllHealthFacilities()
        } coAnswers {
            val array = JSONArray(HEALTH_FACILITY_JSON)
            val healthFacilities = array.map(
                JSONArray::getJSONObject,
                HealthFacility.Companion::unmarshal
            )
            Success(healthFacilities, 200)
        }

        coEvery {
            getAllPatientsStreaming(any())
        } coAnswers {
            // Simulate the network sending over the JSON as a stream of bytes.
            val inputStreamBlock = arg<suspend (InputStream) -> Unit>(0)
            val jsonStream = CommonPatientReadingJsons
                .allPatientsJsonExpectedPair.first
                .byteInputStream()
            inputStreamBlock(jsonStream)
            Success(Unit, 200)
        }
    }

    private val fakeSharedPreferences = mutableMapOf<String, Any?>()
    private val fakeHealthFacilityDatabase = mutableListOf<HealthFacility>()
    private val fakePatientDatabase = mutableListOf<Patient>()
    private val fakeReadingDatabase = mutableListOf<Reading>()

    @Suppress("unused")
    private val mockDatabase = mockk<CradleDatabase> {
        every { runInTransaction(any()) } answers { arg<Runnable>(0).run() }
        // FIXME: Look into why this causes the entire test to fail (it will freeze the test and
        //  prevent it from completing). Until then, we will
        //  have to mock patient manager directly instead of creating a patient manager with
        //  mocked dependencies.
        //coEvery { withTransaction<Unit>(any()) } coAnswers {
        // For whatever reason, this is broken
        //firstArg<suspend () -> Unit>().invoke()
        //}
        every { clearAllTables() } answers {
            fakeHealthFacilityDatabase.clear()
            fakePatientDatabase.clear()
            fakeReadingDatabase.clear()
        }
        every { close() } returns Unit
    }

    private val mockPatientDao = mockk<PatientDao> {
        every { insert(any()) } answers {
            fakePatientDatabase.add(firstArg())
            5L
        }
    }
    private val mockReadingDao = mockk<ReadingDao> {
        every { insertAll(any()) } answers {
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
        every { insert(any()) } answers {
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

            every { commit() } returns true
            every { apply() } returns Unit
            every { clear() } answers {
                fakeSharedPreferences.clear()
                this@editor
            }
        }
    }

    private val mockPatientManager = mockk<PatientManager> {
        coEvery { addPatientWithReadings(any(), any(), any(), any()) } coAnswers {
            val patient = arg<Patient>(0)
            val readings = arg<List<Reading>>(1)
            val areReadingsFromServer = arg<Boolean>(2)
            val isPatientNew = arg<Boolean>(3)
            if (areReadingsFromServer) {
                readings.forEach { it.isUploadedToServer = true }
            }
            if (isPatientNew) {
                mockPatientDao.insert(patient)
            } else {
                mockPatientDao.updateOrInsertIfNotExists(patient)
            }
            mockReadingDao.insertAll(secondArg())
            delay(25L)
        }
    }

    private val mockHealthManager = mockk<HealthFacilityManager> {
        coEvery {
            addAll(any())
        } coAnswers {
            val healthFacilities: List<HealthFacility> = arg(0) ?: return@coAnswers
            fakeHealthFacilityDatabase.addAll(healthFacilities)
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

    @Test
    fun `login with right credentials`() {
        runBlocking {
            val loginManager = LoginManager(
                mockRestApi,
                mockSharedPrefs,
                mockDatabase,
                mockPatientManager,
                mockHealthManager,
                mockContext
            )

            val result = loginManager.login(TEST_USER_EMAIL, TEST_USER_PASSWORD)
            assert(result is Success) {
                "expected login to be successful, but it failed, with " +
                    "result $result and\n" +
                    "shared prefs map $fakeSharedPreferences"
            }

            assertEquals(TEST_AUTH_TOKEN, fakeSharedPreferences["token"]) { "bad auth token" }
            assertEquals(TEST_USER_ID, fakeSharedPreferences["userId"]) { "bad userId" }
            assertEquals(
                TEST_FIRST_NAME,
                fakeSharedPreferences[mockContext.getString(R.string.key_vht_name)]
            ) { "bad first name" }

            val latestVersion = SharedPreferencesMigration.LATEST_SHARED_PREF_VERSION
            val versionStored = fakeSharedPreferences[
                SharedPreferencesMigration.KEY_SHARED_PREFERENCE_VERSION] as? Int
            assertEquals(latestVersion, versionStored) {
                "expected shared pref version to be $latestVersion, but got $versionStored"
            }

            val userSelectedHealthFacilities = fakeHealthFacilityDatabase
                .filter { it.isUserSelected }
            assertEquals(1, userSelectedHealthFacilities.size) {
                "LoginManager failed to select a health facility"
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

            // Verify that the streamed parsing via Jackson was correct.
            val expectedPatientAndReadings = CommonPatientReadingJsons
                .allPatientsJsonExpectedPair.second
            expectedPatientAndReadings.forEach { patientAndReadings ->
                val (expectedPatient, expectedReadings) =
                    patientAndReadings.patient to patientAndReadings.readings.map {
                        it.copy(isUploadedToServer = true)
                    }

                // The patient ID must have been parsed correctly at least since that's the
                // primary key. When we find a match, we then check to see if all of the fields
                // are the same.
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
    fun `login with wrong credentials`() {
        runBlocking {
            val loginManager = LoginManager(
                mockRestApi,
                mockSharedPrefs,
                mockDatabase,
                mockPatientManager,
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

            assertEquals(0, fakeSharedPreferences.keys.size)
            val healthFacilities = fakeHealthFacilityDatabase
            assertEquals(0, healthFacilities.size) { "nothing should be added" }
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }
        }
    }
}