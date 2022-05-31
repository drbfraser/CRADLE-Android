package com.cradleplatform.neptune.net

data class HealthFacilitySyncResult(
    val networkResult: NetworkResult<Unit>,
    var totalHealthFacilitiesDownloaded: Int,
)