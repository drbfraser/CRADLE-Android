package com.cradleplatform.neptune.sync.workers

import com.cradleplatform.neptune.http_sms_service.http.FormResponseSyncResult
import com.cradleplatform.neptune.http_sms_service.http.FormSyncResult
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

internal class FormSyncStagesTest {
    @Test
    fun `form responses sync is attempted after template failures`() = runTest {
        val calls = mutableListOf<String>()
        val templateFailure = FormSyncResult(NetworkResult.Failure(ByteArray(0), 503), 0)
        val responseSuccess = FormResponseSyncResult(NetworkResult.Success(Unit, 200), 1)

        val result = runFormSyncStages(
            syncV2Templates = {
                calls.add("v2 templates")
                templateFailure
            },
            syncV1Templates = {
                calls.add("v1 templates")
                templateFailure
            },
            syncFormResponses = {
                calls.add("form responses")
                responseSuccess
            }
        )

        assertEquals(listOf("v2 templates", "v1 templates", "form responses"), calls)
        assertEquals(responseSuccess, result.formResponses)
    }
}
