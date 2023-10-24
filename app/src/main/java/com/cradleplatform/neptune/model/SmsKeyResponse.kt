package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("ConstructorParameterNaming")
data class SmsKeyResponse(
    @JsonProperty("message")
    var message: String,
    @JsonProperty("expiry_date")
    var expiry_date: String,
    @JsonProperty("stale_date")
    var stale_date: String,
    @JsonProperty("sms_key")
    var sms_key: String
)
