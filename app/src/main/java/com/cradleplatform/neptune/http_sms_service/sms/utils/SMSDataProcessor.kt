package com.cradleplatform.neptune.http_sms_service.sms.utils

import android.net.Uri
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.SmsReadingWithReferral
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.google.gson.GsonBuilder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class to transform Data objects into a JSON String that can be read by Cradle Backend's
 * SMS API Endpoint after being relayed and decrypted, which will forward to other internal API
 * Endpoints.
 */
@Singleton
class SMSDataProcessor @Inject constructor(private val urlManager: UrlManager) {
    // TODO: Add target API endpoint information needed by the backend to json ??
    // TODO: requestNumber=0 as it is not implemented in the backend yet

    fun processRequestDataToJSON(method: Http.Method, url: String, headers: String, body: ByteArray): String {
        val uri = Uri.parse(url)
        val endpoint = uri.path ?: throw Exception("URL path is null")
        return JacksonMapper.createWriter<SmsJsonData>().writeValueAsString(
            SmsJsonData(
                requestNumber = "0",
                method = method.name,
                endpoint = endpoint,
                headers = headers,
                body = body.decodeToString()
            )
        )
    }
}

data class SmsJsonData(
    val requestNumber: String,
    val method: String,
    val endpoint: String,
    val headers: String,
    val body: String
)
