package com.cradleVSA.neptune.manager

import com.cradleVSA.neptune.net.NetworkResult
import com.cradleVSA.neptune.net.RestApi
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

class VersionManager @Inject constructor(
    private val restApi: RestApi
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Version(
        @JsonProperty("version")
        val version: String
    )

    suspend fun getAPIVersion(): NetworkResult<Version> {
        return restApi.getAPIVersion()
    }

}
