package com.cradleplatform.neptune.http_sms_service.http

/**
 * A record that the server didn't accept during a sync: the ID of the record, and the reason.
 */
data class SyncError(
    val recordId: String,
    val message: String,
)

data class HealthFacilitySyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalHealthFacilitiesDownloaded: Int,
)

data class PatientSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalPatientsUploaded: Int,
    var totalPatientsDownloaded: Int,
    var errors: String?,
)

data class ReadingSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalReadingsUploaded: Int,
    var totalReadingsDownloaded: Int,
    val errors: List<SyncError> = emptyList(),
)

data class ReferralSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalReferralsUploaded: Int,
    var totalReferralsDownloaded: Int,
    val errors: List<SyncError> = emptyList(),
)

data class AssessmentSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalAssessmentsUploaded: Int,
    var totalAssessmentsDownloaded: Int,
    var errors: String?,
)

data class FormSyncResult(
    val networkResult: NetworkResult<Unit>,
    val totalFormClassDownloaded: Int
)

data class FormResponseSyncResult(
    val networkResult: NetworkResult<Unit>,
    val totalFormResponsesDownloaded: Int
)

data class WorkflowTemplateSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalWorkflowTemplatesDownloaded: Int
)

data class WorkflowInstanceSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalWorkflowInstancesUploaded: Int,
    var totalWorkflowInstancesDownloaded: Int,
    val errors: List<SyncError> = emptyList(),
)
