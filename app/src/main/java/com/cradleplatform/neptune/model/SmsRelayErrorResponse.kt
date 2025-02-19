package com.cradleplatform.neptune.model

import com.google.gson.annotations.SerializedName

data class SmsRelayErrorResponse (

    @SerializedName("message")
    var message: String,

    @SerializedName("expected_request_number")
    var expectedRequestNumber: Int?
)