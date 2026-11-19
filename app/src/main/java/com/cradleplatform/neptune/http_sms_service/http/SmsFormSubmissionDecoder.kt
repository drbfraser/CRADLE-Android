package com.cradleplatform.neptune.http_sms_service.http

import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormSubmissionV2
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.IOException

/** SMS relay success wraps the destination API's status and JSON body. */
internal object SmsFormSubmissionDecoder {
    fun decode(message: String, request: CreateFormSubmissionRequestV2): NetworkResult<FormSubmissionV2> {
        return try {
            val envelope = JsonParser().parse(message).asJsonObject
            val code = envelope.get("code").asInt
            val body = envelope.get("body").asString
            if (code !in 200..299) {
                NetworkResult.Failure(body.toByteArray(), code)
            } else {
                val submission = Gson().fromJson(body, FormSubmissionV2::class.java)
                require(code == 201 && submission != null && !submission.id.isNullOrBlank()) {
                    "Missing submission confirmation."
                }
                require(submission.id == request.id &&
                    submission.formTemplateId == request.formTemplateId &&
                    submission.patientId == request.patientId) {
                    "SMS confirmation does not match this submission."
                }
                NetworkResult.Success(submission, code)
            }
        } catch (exception: Exception) {
            NetworkResult.NetworkException(IOException("Invalid SMS submission response; keep the draft.", exception))
        }
    }
}
