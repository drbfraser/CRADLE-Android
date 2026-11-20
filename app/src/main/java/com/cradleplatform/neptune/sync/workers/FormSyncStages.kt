package com.cradleplatform.neptune.sync.workers

import com.cradleplatform.neptune.http_sms_service.http.FormResponseSyncResult
import com.cradleplatform.neptune.http_sms_service.http.FormSyncResult

internal data class FormSyncStages(
    val v2Templates: FormSyncResult,
    val v1Templates: FormSyncResult,
    val formResponses: FormResponseSyncResult
)

internal suspend fun runFormSyncStages(
    syncV2Templates: suspend () -> FormSyncResult,
    syncV1Templates: suspend () -> FormSyncResult,
    syncFormResponses: suspend () -> FormResponseSyncResult
): FormSyncStages {
    val v2Templates = syncV2Templates()
    val v1Templates = syncV1Templates()
    val formResponses = syncFormResponses()
    return FormSyncStages(v2Templates, v1Templates, formResponses)
}
