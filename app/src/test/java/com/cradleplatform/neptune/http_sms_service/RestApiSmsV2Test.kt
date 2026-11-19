package com.cradleplatform.neptune.http_sms_service

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.lifecycle.MutableLiveData
import com.cradleplatform.neptune.http_sms_service.http.*
import com.cradleplatform.neptune.http_sms_service.sms.*
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.extensions.InstantExecutorExtension
import com.cradleplatform.neptune.viewmodel.UserViewModel
import com.google.gson.Gson
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(InstantExecutorExtension::class)
internal class RestApiSmsV2Test {
    private val context = mockk<Context>(relaxed = true)
    private val preferences = mockk<SharedPreferences>(relaxed = true)
    private val urls = mockk<UrlManager>()
    private val http = mockk<Http>()
    private val reporter = mockk<SmsStateReporter>(relaxed = true)
    private val sender = mockk<SMSSender>(relaxed = true)
    private val receiver = mockk<SMSReceiver>(relaxed = true)
    private val processor = mockk<SMSDataProcessor>()
    private val state = MutableLiveData(SmsTransmissionStates.GETTING_READY_TO_SEND)
    private val code = MutableLiveData(0)
    private val message = MutableLiveData("")
    private val request = CreateFormSubmissionRequestV2(
        id = "sub-1", formTemplateId = "template-1", patientId = "patient-1", answers = emptyList()
    )
    private lateinit var api: RestApi

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { preferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null) } returns "+15555555555"
        every { urls.formsV2Submissions } returns "https://example.test/api/forms/v2/submissions"
        every { reporter.stateToCollect } returns state
        every { reporter.statusCode } returns code
        every { reporter.statusCodeToCollect } returns code
        every { reporter.decryptedMsgLiveData } returns message
        every { reporter.errorMessageToCollect } returns MutableLiveData("validation failed")
        every { processor.processRequestDataToJSON(any(), any(), any(), any()) } returns "payload"
        every { sender.queueRelayContent("payload") } returns true
        api = RestApi(context, preferences, urls, http, reporter, sender, receiver, processor)
    }

    @AfterEach
    fun tearDown() { Dispatchers.resetMain() }

    private fun respond(status: Int) {
        code.value = status
        message.value = Gson().toJson(mapOf("code" to status, "body" to
            """{"id":"sub-1","formTemplateId":"template-1","patientId":"patient-1"}"""))
        state.value = SmsTransmissionStates.WAITING_FOR_USER_RESPONSE
    }

    @Test
    fun `SMS completes without dialog and preserves inner 201`() = runBlocking {
        every { sender.sendSmsMessage(false) } answers { respond(201) }
        val result = withTimeout(5000) { api.postFormSubmissionV2(request, Protocol.SMS) }
        assertTrue(result is NetworkResult.Success)
        assertEquals(201, (result as NetworkResult.Success).statusCode)
        verify { receiver.unregister(); sender.reset(); reporter.resetStateReporter() }
        confirmVerified(http)
    }

    @Test
    fun `inner failure completes without waiting for dialog cancellation`() = runBlocking {
        every { sender.sendSmsMessage(false) } answers { respond(422) }
        val result = withTimeout(5000) { api.postFormSubmissionV2(request, Protocol.SMS) }
        assertTrue(result is NetworkResult.Failure)
        assertEquals(422, (result as NetworkResult.Failure).statusCode)
        verify { receiver.unregister() }
    }

    @Test
    fun `missing SMS key queue failure returns immediately and cleans up`() = runBlocking {
        every { sender.queueRelayContent("payload") } returns false
        assertTrue(api.postFormSubmissionV2(request, Protocol.SMS) is NetworkResult.NetworkException)
        verify(exactly = 0) { sender.sendSmsMessage(any()) }
        verify { receiver.unregister(); sender.reset() }
    }

    @Test
    fun `missing relay number fails before registration`() = runBlocking {
        every { preferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null) } returns null
        assertTrue(api.postFormSubmissionV2(request, Protocol.SMS) is NetworkResult.NetworkException)
        verify(exactly = 0) { receiver.register() }
    }
    @Test
    fun `busy session is rejected and cancellation releases it for the next request`() = runBlocking {
        val sent = CompletableDeferred<Unit>()
        every { sender.sendSmsMessage(false) } answers { sent.complete(Unit); Unit }
        val first = async { api.postFormSubmissionV2(request, Protocol.SMS) }
        withTimeout(5000) { sent.await() }
        val busy = api.postFormSubmissionV2(request, Protocol.SMS)
        assertTrue(busy is NetworkResult.NetworkException)
        verify(exactly = 1) { receiver.register() }
        first.cancelAndJoin()
        verify(exactly = 1) { receiver.unregister(); sender.reset() }

        every { sender.sendSmsMessage(false) } answers { respond(201) }
        assertTrue(withTimeout(5000) { api.postFormSubmissionV2(request, Protocol.SMS) } is NetworkResult.Success)
    }

    @Test
    fun `denied permission fails before registration`() = runBlocking {
        every { context.checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_DENIED
        assertTrue(api.postFormSubmissionV2(request, Protocol.SMS) is NetworkResult.NetworkException)
        verify(exactly = 0) { receiver.register() }
    }

}
