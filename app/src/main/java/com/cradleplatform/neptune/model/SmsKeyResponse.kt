package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonProperty

@Suppress("ConstructorParameterNaming")
data class SmsKeyResponse(
    @JsonProperty("message")
    var message: String,
    @JsonProperty("expiryDate")
    var expiryDate: String,
    @JsonProperty("staleDate")
    var staleDate: String,
    @JsonProperty("smsDey")
    var smsKey: String
)
