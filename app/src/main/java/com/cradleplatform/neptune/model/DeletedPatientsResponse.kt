package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Response of POST /api/sync/deleted_patients: the IDs (of those sent by the device)
 * that were deleted on the server and that this user could access.
 */
data class DeletedPatientsResponse(
    @JsonProperty("deletedPatientIds")
    val deletedPatientIds: List<String>
)
