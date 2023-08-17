package com.cradleplatform.neptune.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SmsSecretKey(
    @JsonProperty("sms_key")
    val smsKey: String,
    @JsonProperty("stale_date")
    val staleDate: String,
    @JsonProperty("expiry_date")
    val expiryDate: String,
    @JsonProperty("message")
    val message: String
)
