package com.cradleplatform.neptune.model

import com.google.gson.annotations.SerializedName

data class SmsRelayErrorResponse(

    @SerializedName("message")
    val message: String,

    @SerializedName("expected_request_number")
    val expectedRequestNumber: Int?
)
