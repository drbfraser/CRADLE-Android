package com.cradleplatform.neptune.http_sms_service

import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.SmsFormSubmissionDecoder
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SmsFormSubmissionDecoderTest {
    private val request = CreateFormSubmissionRequestV2(
        id = "submission-1", formTemplateId = "template-1", patientId = "patient-1", answers = emptyList()
    )
    private val body = """{"id":"submission-1","formTemplateId":"template-1","patientId":"patient-1",
        "userId":1,"dateSubmitted":123,"lastEdited":123,"lang":"English","archived":false}"""

    private fun envelope(code: Int, body: String) = Gson().toJson(mapOf("code" to code, "body" to body))

    @Test
    fun `inner 201 is decoded using V2 field names and preserved`() {
        val result = SmsFormSubmissionDecoder.decode(envelope(201, body), request)
        assertTrue(result is NetworkResult.Success)
        result as NetworkResult.Success
        assertEquals(201, result.statusCode)
        assertEquals("submission-1", result.value.id)
        assertEquals("template-1", result.value.formTemplateId)
    }

    @Test
    fun `outer success does not turn inner errors into success`() {
        for (code in listOf(403, 409, 422)) {
            val result = SmsFormSubmissionDecoder.decode(envelope(code, "validation failed"), request)
            assertTrue(result is NetworkResult.Failure)
            result as NetworkResult.Failure
            assertEquals(code, result.statusCode)
            assertEquals("validation failed", result.body.decodeToString())
        }
    }

    @Test
    fun `malformed or mismatched confirmations never confirm submission`() {
        for (message in listOf("", "{}", envelope(201, "null"), envelope(201, "{}"),
            envelope(200, body), envelope(201, body.replace("submission-1", "other")),
            envelope(201, body.replace("patient-1", "other")), envelope(201, body.replace("template-1", "other")))) {
            assertTrue(SmsFormSubmissionDecoder.decode(message, request) is NetworkResult.NetworkException)
        }
    }
}
