package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class RelayPhoneNumber(
    @JsonProperty("phoneNumber")
    val phoneNumber: String
)
