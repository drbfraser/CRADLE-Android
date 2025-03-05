package com.cradleplatform.neptune.model

import com.google.gson.annotations.SerializedName

data class SmsRelayErrorResponse425(
    @SerializedName("message")
    val message: String,

    @SerializedName("expected_request_number")
    val expectedRequestNumber: Int?
)