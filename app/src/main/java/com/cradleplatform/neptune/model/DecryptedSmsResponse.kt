package com.cradleplatform.neptune.model

data class DecryptedSmsResponse(
    val code: Int,
    val body: String,
)