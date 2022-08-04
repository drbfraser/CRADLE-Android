package com.cradleplatform.neptune.net

import android.util.Log
import com.cradleplatform.neptune.model.CommonPatientReadingJsons
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.testutils.MockWebServerUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection.HTTP_OK

internal class RestApiTestForPatients {

    private lateinit var mockServer: MockWebServer
    private lateinit var restApi: RestApi

    @BeforeEach
    fun setUp() {

        val (_restApi, _mockServer) =
            MockWebServerUtils.createRestApiWithBlankServer()
        restApi = _restApi
        mockServer = _mockServer

        // ignore any android log calls
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
    fun `Test get All Patients`(){
        MockWebServerUtils.enqueueResponseToBlankServer(
            mockServer,
            HTTP_OK,
            CommonPatientReadingJsons.allPatientsJsonExpectedPair.first
        )

        val patientChannelList = mutableListOf<Patient>()
        val patientChannel = mockk<SendChannel<Patient>>{
            coEvery {send(any())} answers  {
                patientChannelList.add(firstArg())
            }
            every {close()} returns true
        }
        runBlocking {
            restApi.getAllPatients(patientChannel)
        }

        assert(patientChannelList.count() == 4)

        //TODO: confirm model between android and server, before adding
        // assertion checks for the received patients (refer to issue 62)

    }

    @Test
    fun `Test get full detail of a patient (with their reading)`(){

    }

    @Test
    fun `Test get info of a patient`(){

    }

}