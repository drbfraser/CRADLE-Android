package com.cradleplatform.neptune.http_sms_service.sms.utils

import android.net.Uri
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class to transform Data objects into a JSON String that can be read by Cradle Backend's
 * SMS API Endpoint after being relayed and decrypted, which will forward to other internal API
 * Endpoints.
 */
@Singleton
class SMSDataProcessor @Inject constructor(
    private val smsStateReporter: SmsStateReporter
) {
    fun processRequestDataToJSON(
        method: Http.Method,
        url: String,
        headers: Map<String, String>,
        body: ByteArray
    ): String {
        val uri = Uri.parse(url)
        val endpoint = uri.path ?: throw Exception("URL path is null")
        return Gson().toJson(
            SmsJsonData(
                requestNumber = smsStateReporter.getCurrentRequestNumber(),
                method = method.name,
                endpoint = endpoint,
                headers = headers,
                body = body.decodeToString()
            )
        )
    }
}

data class SmsJsonData(
    val requestNumber: Int,
    val method: String,
    val endpoint: String,
    val headers: Map<String, String>,
    val body: String
)
