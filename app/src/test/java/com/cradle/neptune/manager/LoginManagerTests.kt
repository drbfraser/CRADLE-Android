package com.cradle.neptune.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradle.neptune.R
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.daos.PatientDao
import com.cradle.neptune.database.daos.ReadingDao
import com.cradle.neptune.ext.map
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream

@ExperimentalCoroutinesApi
internal class LoginManagerTests {
    private val testDispatcher = TestCoroutineDispatcher()

    companion object {
        private const val PATIENT_AND_READINGS_JSON =
            """
[
    {
        "isPregnant": true,
        "patientName": "Test patient",
        "patientId": "3459834789348",
        "gestationalTimestamp": 1590969549,
        "drugHistory": "Some drug history",
        "dob": "2002-01-08",
        "villageNumber": "133",
        "created": 1604883600,
        "gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_WEEKS",
        "patientSex": "FEMALE",
        "medicalHistory": "Some med history.",
        "zone": "634",
        "isExactDob": true,
        "householdNumber": "95682385",
        "lastEdited": 1604883600,
        "base": 1604883600,
        "readings": [
            {
                "bpSystolic": 119,
                "dateTimeTaken": 1604883580,
                "bpDiastolic": 97,
                "heartRateBPM": 78,
                "respiratoryRate": 65,
                "oxygenSaturation": 98,
                "userId": 10,
                "temperature": 35,
                "patientId": "3459834789348",
                "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                "symptoms": [
                    "BLURRED VISION",
                    "FEVERISH",
                    "LOSS of SENSE",
                    "Other symptoms"
                ],
                "trafficLightStatus": "YELLOW_UP",
                "urineTests": {
                    "urineTestLeuc": "++",
                    "urineTestGlu": "+++",
                    "urineTestPro": "NAD",
                    "readingId": "ca84ac2d-953f-4f5e-ae24-e0a6e8af7c94",
                    "urineTestNit": "NAD",
                    "id": 18,
                    "urineTestBlood": "NAD"
                }
            }
        ]
    },
    {
        "isPregnant": false,
        "patientName": "Another patient",
        "patientId": "123456",
        "gestationalTimestamp": 0,
        "drugHistory": "History",
        "dob": "1974-11-08",
        "villageNumber": "4555",
        "created": 1604883668,
        "gestationalAgeUnit": "GESTATIONAL_AGE_UNITS_WEEKS",
        "patientSex": "MALE",
        "medicalHistory": "",
        "zone": "354",
        "isExactDob": false,
        "householdNumber": "111",
        "lastEdited": 1604883668,
        "base": 1604883668,
        "readings": [
            {
                "bpSystolic": 119,
                "dateTimeTaken": 1604883648,
                "bpDiastolic": 98,
                "heartRateBPM": 87,
                "userId": 10,
                "patientId": "123456",
                "readingId": "777850f0-dc71-4501-a440-1871ecea6381",
                "symptoms": [
                    "NONE"
                ],
                "trafficLightStatus": "YELLOW_UP"
            }
        ]
    }
]
        """

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
            val inputStreamBlock = arg<suspend (InputStream) -> Unit>(0)
            inputStreamBlock.invoke(PATIENT_AND_READINGS_JSON.byteInputStream())
            Success(Unit, 200)
        }
    }

    private val fakeSharedPreferences = mutableMapOf<String, Any?>()
    private val fakeHealthFacilityDatabase = mutableListOf<HealthFacility>()
    private val fakePatientDatabase = mutableListOf<Patient>()
    private val fakeReadingDatabase = mutableListOf<Reading>()

    private val mockDatabase = mockk<CradleDatabase> {
        every { runInTransaction(any()) } answers { arg<Runnable>(0).run() }
    }
    private val mockPatientDao = mockk<PatientDao> {
        every { insert(any()) } answers { fakePatientDatabase.add(firstArg()) }
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
            every {
                putString(any(), any())
            } answers {
                putInFakeSharedPreference<String?>(firstArg(), secondArg())
                this@editor
            }

            every {
                putInt(any(), any())
            } answers {
                putInFakeSharedPreference<Int?>(firstArg(), secondArg())
                this@editor
            }

            every {
                putLong(any(), any())
            } answers {
                putInFakeSharedPreference<Long?>(firstArg(), secondArg())
                this@editor
            }

            every { commit() } returns true
            every { apply() } returns Unit
        }
    }

    private val fakePatientManager = PatientManager(
        mockDatabase,
        mockPatientDao,
        mockReadingDao,
        mockRestApi
    )

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
        Dispatchers.setMain(testDispatcher)

        fakeSharedPreferences.clear()
        fakeHealthFacilityDatabase.clear()
        fakePatientDatabase.clear()
        fakeReadingDatabase.clear()
    }

    @Test
    fun `login with right credentials`() {
        runBlocking {
            val loginManager = LoginManager(
                mockRestApi,
                mockSharedPrefs,
                fakePatientManager,
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

            val userSelectedHealthFacilities = fakeHealthFacilityDatabase
                .filter { it.isUserSelected }
            assertEquals(1, userSelectedHealthFacilities.size) {
                "LoginManager failed to select a health facility"
            }
            assertEquals(TEST_USER_FACILITY_NAME, userSelectedHealthFacilities[0].name) {
                "wrong health facility selected"
            }

            assertEquals(2, fakePatientDatabase.size) { "parsing the patients failed" }
            fakePatientDatabase[0].run {
                assertEquals("3459834789348", id)
                assertEquals("Test patient", name)
            }
            fakePatientDatabase[1].run {
                assertEquals("123456", id)
                assertEquals("Another patient", name)
            }

            assertEquals(2, fakeReadingDatabase.size) { "parsing the readings failed" }
            fakeReadingDatabase[0].run { assertEquals(fakePatientDatabase[0].id, patientId) }
            fakeReadingDatabase[1].run { assertEquals(fakePatientDatabase[1].id, patientId) }
        }
    }

    @Test
    fun `login with wrong credentials`() {
        runBlocking {
            val loginManager = LoginManager(
                mockRestApi,
                mockSharedPrefs,
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

            assertEquals(0, fakeSharedPreferences.keys.size)
            val healthFacilities = fakeHealthFacilityDatabase
            assertEquals(0, healthFacilities.size) { "nothing should be added" }
            assertEquals(0, fakePatientDatabase.size) { "nothing should be added" }
            assertEquals(0, fakeReadingDatabase.size) { "nothing should be added" }
        }
    }
}
