package com.cradleplatform.neptune.http_sms_service.sms.utils

import android.net.Uri
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.SmsReadingWithReferral
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSDataProcessor @Inject constructor(private val urlManager: UrlManager) {
    // TODO: Add target API endpoint information needed by the backend to json ??
    // TODO: requestNumber=0 as it is not implemented in the backend yet
    fun processFormToJSON(formResponse: FormResponse): String {
        val formResponseJSON = Gson().toJson(formResponse)
        val url = Uri.parse(urlManager.uploadFormResponse)
        val endpoint = url.path
        return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
            SmsReadingWithReferral(
                requestNumber = "0",
                method = "POST",
                endpoint = endpoint!!,
                headers = "",
                body = formResponseJSON
            )
        )
    }
    fun processPatientAndReadingsToJSON(patientAndReadings: PatientAndReadings): String {
        if (patientAndReadings.patient.lastServerUpdate == null) {
            val patientAndReadingsJSON = JacksonMapper.createWriter<PatientAndReadings>().writeValueAsString(
                patientAndReadings
            )
            val url = Uri.parse(urlManager.postPatient)
            val endpoint = url.path
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint!!,
                    headers = "",
                    body = patientAndReadingsJSON
                )
            )
        } else {
            val readingJSON = JacksonMapper.createWriter<Reading>().writeValueAsString(
                patientAndReadings.readings[0])

            val url = Uri.parse(urlManager.postReading)
            val endpoint = url.path
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint!!,
                    headers = "",
                    body = readingJSON
                )
            )
        }
    }
    fun processPatientAndReferralToJSON(patientAndReferrals: PatientAndReferrals): String {
        val patientAndReferralsJSON = JacksonMapper.createWriter<PatientAndReferrals>()
            .writeValueAsString(patientAndReferrals)
        val url = Uri.parse(urlManager.postPatient)
        val endpoint = url.path
        return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
            SmsReadingWithReferral(
                requestNumber = "0",
                method = "POST",
                endpoint = endpoint!!,
                headers = "",
                body = patientAndReferralsJSON
            )
        )
    }
    fun processReferralToJSON(referral: Referral): String {
        val referralJSON = JacksonMapper.createWriter<Referral>().writeValueAsString(
            referral
        )
        val url = Uri.parse(urlManager.postReferral)
        val endpoint = url.path
        return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
            SmsReadingWithReferral(
                requestNumber = "0",
                method = "POST",
                endpoint = endpoint!!,
                headers = "",
                body = referralJSON
            )
        )
    }
}
