package com.cradleplatform.neptune.http_sms_service.http

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
    var totalReadingsDownloaded: Int
)

data class ReferralSyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalReferralsUploaded: Int,
    var totalReferralsDownloaded: Int,
    var errors: String?,
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
