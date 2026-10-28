package com.cradleplatform.neptune.manager

import androidx.room.withTransaction
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.WorkflowInstanceDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class PatientManagerDeleteTests {
    private lateinit var database: CradleDatabase
    private lateinit var patientDao: PatientDao
    private lateinit var workflowInstanceDao: WorkflowInstanceDao
    private lateinit var restApi: RestApi
    private lateinit var patientManager: PatientManager

    @BeforeEach
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        database = mockk()
        coEvery { database.withTransaction(captureLambda<suspend () -> Unit>()) } coAnswers {
            lambda<suspend () -> Unit>().captured.invoke()
        }

        patientDao = mockk(relaxed = true)
        workflowInstanceDao = mockk(relaxed = true)
        restApi = mockk()
        patientManager = PatientManager(
            database,
            patientDao,
            mockk<ReadingDao>(),
            workflowInstanceDao,
            restApi
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `removePatientsDeletedOnServer removes the patients the server returns`() = runBlocking {
        coEvery { patientDao.getSyncedPatientIds() } returns listOf("1", "2", "3")
        coEvery { restApi.syncDeletedPatients(listOf("1", "2", "3")) } returns
            NetworkResult.Success(listOf("1", "3"), 200)

        val removed = patientManager.removePatientsDeletedOnServer()

        assertEquals(2, removed)
        coVerify(exactly = 1) { workflowInstanceDao.deleteByPatientIds(listOf("1", "3")) }
        coVerify(exactly = 1) { patientDao.deleteById("1") }
        coVerify(exactly = 1) { patientDao.deleteById("3") }
        coVerify(exactly = 0) { patientDao.deleteById("2") }
    }

    @Test
    fun `removePatientsDeletedOnServer removes nothing when none were deleted`() = runBlocking {
        coEvery { patientDao.getSyncedPatientIds() } returns listOf("1", "2")
        coEvery { restApi.syncDeletedPatients(any()) } returns NetworkResult.Success(emptyList(), 200)

        val removed = patientManager.removePatientsDeletedOnServer()

        assertEquals(0, removed)
        coVerify(exactly = 0) { patientDao.deleteById(any()) }
        coVerify(exactly = 0) { workflowInstanceDao.deleteByPatientIds(any()) }
    }

    @Test
    fun `removePatientsDeletedOnServer skips the server when no patients are synced`() = runBlocking {
        coEvery { patientDao.getSyncedPatientIds() } returns emptyList()

        val removed = patientManager.removePatientsDeletedOnServer()

        assertEquals(0, removed)
        coVerify(exactly = 0) { restApi.syncDeletedPatients(any()) }
        coVerify(exactly = 0) { patientDao.deleteById(any()) }
    }

    @Test
    fun `removePatientsDeletedOnServer returns null and removes nothing on a server error`() =
        runBlocking {
            coEvery { patientDao.getSyncedPatientIds() } returns listOf("1")
            coEvery { restApi.syncDeletedPatients(any()) } returns
                NetworkResult.Failure(ByteArray(0), 500)

            val removed = patientManager.removePatientsDeletedOnServer()

            assertNull(removed)
            coVerify(exactly = 0) { patientDao.deleteById(any()) }
            coVerify(exactly = 0) { workflowInstanceDao.deleteByPatientIds(any()) }
        }

    @Test
    fun `deletePatients does nothing for an empty list`() = runBlocking {
        patientManager.deletePatients(emptyList())

        coVerify(exactly = 0) { database.withTransaction(any<suspend () -> Unit>()) }
        coVerify(exactly = 0) { patientDao.deleteById(any()) }
        coVerify(exactly = 0) { workflowInstanceDao.deleteByPatientIds(any()) }
    }
}
