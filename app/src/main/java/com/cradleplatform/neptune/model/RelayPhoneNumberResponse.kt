package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonProperty

data class RelayPhoneNumberResponse(
    @JsonProperty("relayPhoneNumbers")
    val relayPhoneNumbers: List<String>
)