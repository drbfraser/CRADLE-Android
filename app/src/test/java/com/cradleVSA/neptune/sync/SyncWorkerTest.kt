package com.cradleVSA.neptune.sync;

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.manager.PatientManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.model.CommonPatientReadingJsons
import com.cradleVSA.neptune.model.HealthFacility
import com.cradleVSA.neptune.model.Patient
import com.cradleVSA.neptune.model.Reading
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.testutils.MockDependencyUtils
import com.cradleVSA.neptune.testutils.MockWebServerUtils
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.clearAllMocks
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

class SyncWorkerTest {
    private val testMainDispatcher = TestCoroutineDispatcher()

    companion object {
        private const val TEST_AUTH_TOKEN = "sOmEaUtHToken"
    }

    private val fakePatientDatabase: MutableList<Patient>
    private val fakeReadingDatabase: MutableList<Reading>
    private val mockDatabase: CradleDatabase
    private val mockPatientDao: PatientDao
    private val mockReadingDao: ReadingDao
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
            fakePatientDatabase = underlyingPatientDatabase
            fakeReadingDatabase = underlyingReadingDatabase
            fakeHealthFacilityDatabase = underlyingHealthFacilityDatabase
        }

        mockSharedPrefs.edit {
            putString(LoginManager.TOKEN_KEY, TEST_AUTH_TOKEN)
        }
    }

    private val mockRestApi: RestApi
    private val mockWebServer: MockWebServer
    private var failTheLoginWithIOIssues = false
    init {
        val (api, server) = MockWebServerUtils.createRestApiWithMockedServer(mockSharedPrefs) {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().apply response@{
                    if (request.path?.matches("/api/sync/patients\\?since=([^&]+)".toRegex()) == true) {
                        if (request.headers["Authorization"] != "Bearer $TEST_AUTH_TOKEN") {
                            setResponseCode(401)
                            setBody(request.headers.toString())
                            return@response
                        }

                        val syncTime = request.requestUrl?.queryParameter("since")
                            ?.toLongOrNull()
                        if (syncTime == null) {
                            setResponseCode(500)
                            return@response
                        }

                        val allPatientsJson =
                            CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
                        val json = """
                            {
                                "${PatientSyncField.TOTAL}": ${CommonPatientReadingJsons.allPatientsJsonExpectedPair.second.size},
                                "${PatientSyncField.PATIENTS}": $allPatientsJson
                            }
                        """.trimIndent()
                        setResponseCode(200)
                        setBody(json)
                    } else if (request.path?.matches("/api/sync/readings\\?since=([^&]+)".toRegex()) == true) {
                        if (request.headers["Authorization"] != "Bearer $TEST_AUTH_TOKEN") {
                            setResponseCode(401)
                            setBody(request.headers.toString())
                            return@response
                        }

                        val syncTime = request.requestUrl?.queryParameter("since")
                            ?.toLongOrNull()
                        if (syncTime == null) {
                            setResponseCode(500)
                            return@response
                        }
                        setResponseCode(500)
                    } else {
                        setResponseCode(404)
                    }
                }
            }
        }
        mockRestApi = api
        mockWebServer = server
    }

    private val mockHealthManager = HealthFacilityManager(mockDatabase)

    private val fakePatientManager = PatientManager(
        mockDatabase,
        mockPatientDao,
        mockReadingDao,
        mockRestApi
    )

    private val fakeReadingManager = ReadingManager(
        mockReadingDao,
        mockRestApi
    )

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val serialExecutor = mockk<SerialExecutor>()
    private val mockContext = mockk<Context>() {
        every { getString(any()) } answers { getMockStringFromResId(firstArg()) }
        every { getString(any(), *anyVararg()) } answers {
            val formatArgs = args[1] as Array<*>
            getMockStringFromResId(firstArg(), *formatArgs)
        }
    }

    private fun getMockStringFromResId(resId: Int, vararg formatArgs: Any?): String = when (resId) {
        R.string.network_result_error_server_rejected_credentials -> "server rejected credentials (401)"
        R.string.network_result_error_server_rejected_upload_request -> "server rejected upload request (400)"
        R.string.network_result_error_server_rejected_url -> "server gave 404"
        R.string.network_result_error_reading_or_patient_might_already_exist -> "server says conflict (409)"
        R.string.network_result_error_generic_status_code -> "server gave generic error: status code %d"
        R.string.sync_worker_failure_server_sent_error_code_d__s -> "Last sync failed: Server sent error code %d. %s"
        R.string.sync_worker_failure_exception_during_sync_s__s -> "Last sync failed: Exception during sync (%s): %s"
        else -> "Unmocked string for resource id $resId: format args is ${formatArgs.toList()}"
    }.let { if (formatArgs.isNotEmpty()) it.format(*formatArgs) else it }

    private val mockTaskExecutor = mockk<TaskExecutor>() {
        every { backgroundExecutor } returns serialExecutor
    }
    private val mockProgressUpdater = mockk<ProgressUpdater>() {
        every { updateProgress(any(), any(), any()) } returns mockk<ListenableFuture<Void>>() {
            every { isDone } returns true
            every { get() } returns mockk()
        }
    }
    private val workParamId = UUID.randomUUID()
    private val mockWorkerParams = mockk<WorkerParameters>() {
        every { taskExecutor } returns mockTaskExecutor
        every { progressUpdater } returns mockProgressUpdater
        every { id } returns workParamId
    }

    @ExperimentalCoroutinesApi
    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        Dispatchers.setMain(testMainDispatcher)
        fakeHealthFacilityDatabase.clear()
        fakePatientDatabase.clear()
        fakeReadingDatabase.clear()
    }

    @ExperimentalCoroutinesApi
    @AfterEach
    fun cleanUp() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
        clearAllMocks()
    }

    @ExperimentalTime
    @Test
    fun `test SyncWorker`() {
        val syncWorker = SyncWorker(
            mockContext,
            mockWorkerParams,
            mockRestApi,
            fakePatientManager,
            fakeReadingManager,
            mockSharedPrefs,
            mockDatabase
        )

        runBlocking {
            withTimeout(10.seconds) {
                val result = syncWorker.doWork()
                assert(result is ListenableWorker.Result.Success) {
                    result as ListenableWorker.Result.Failure
                    val workData = WorkInfo(
                        UUID.randomUUID(),
                        WorkInfo.State.FAILED,
                        result.outputData,
                        emptyList(),
                        result.outputData,
                        0
                    )
                    "failure: here is a dump ${result.outputData}"
                }
            }
        }
    }
}