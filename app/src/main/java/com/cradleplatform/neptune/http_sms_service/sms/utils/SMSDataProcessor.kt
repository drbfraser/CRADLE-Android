package com.cradleplatform.neptune.http_sms_service.sms.utils

import android.net.Uri
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.FormResponse
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

    /**
     * Transforms a Patient into a Cradle Server SMS API compatible JSON String.
     * @param patient
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processPatientToJSON(patient: Patient): String {
        try {
            val patientJSON = JacksonMapper.createWriter<Patient>().writeValueAsString(patient)
            val url = Uri.parse(urlManager.postPatient)
            val endpoint = url.path ?: throw Exception("URL path is null")
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint,
                    headers = "",
                    body = patientJSON
                )
            )
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }

    /**
     * Transforms a Assessment into a Cradle Server SMS API compatible JSON String.
     * @param assessment
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processAssessmentToJSON(assessment: Assessment): String {
        try {
            val assessmentJSON = JacksonMapper.createWriter<Patient>().writeValueAsString(assessment)
            val url = Uri.parse(urlManager.postAssessment)
            val endpoint = url.path ?: throw Exception("URL path is null")
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint,
                    headers = "",
                    body = assessmentJSON
                )
            )
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }

    /**
     * Transforms a FormResponse into a Cradle Server SMS API compatible JSON String.
     * @param formResponse
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processFormToJSON(formResponse: FormResponse): String {
        try {
            val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
            val formResponseJSON = gson.toJson(formResponse)
            val url = Uri.parse(urlManager.uploadFormResponse)
            val endpoint = url.path ?: throw Exception("URL path is null")
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint,
                    headers = "",
                    body = formResponseJSON
                )
            )
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }

    /**
     * Transforms a PatientAndReadings into a Cradle Server SMS API compatible JSON String.
     * @param patientAndReadings
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processPatientAndReadingsToJSON(patientAndReadings: PatientAndReadings): String {
        try {
            if (patientAndReadings.patient.lastServerUpdate == null) {
                val patientAndReadingsJSON = JacksonMapper.createWriter<PatientAndReadings>().writeValueAsString(
                    patientAndReadings
                )
                val url = Uri.parse(urlManager.postPatient)
                val endpoint = url.path ?: throw Exception("URL path is null")
                return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                    SmsReadingWithReferral(
                        requestNumber = "0",
                        method = "POST",
                        endpoint = endpoint,
                        headers = "",
                        body = patientAndReadingsJSON
                    )
                )
            } else {
                val readingJSON = JacksonMapper.createWriter<Reading>().writeValueAsString(
                    patientAndReadings.readings[0])
                val url = Uri.parse(urlManager.postReading)
                val endpoint = url.path ?: throw Exception("URL path is null")
                return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                    SmsReadingWithReferral(
                        requestNumber = "0",
                        method = "POST",
                        endpoint = endpoint,
                        headers = "",
                        body = readingJSON
                    )
                )
            }
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }

    /**
     * Transforms a PatientAndReferrals into a Cradle Server SMS API compatible JSON String.
     * @param patientAndReferrals
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processPatientAndReferralToJSON(patientAndReferrals: PatientAndReferrals): String {
        try {
            val patientAndReferralsJSON = JacksonMapper.createWriter<PatientAndReferrals>()
                .writeValueAsString(patientAndReferrals)
            val url = Uri.parse(urlManager.postPatient)
            val endpoint = url.path ?: throw Exception("URL path is null")
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint,
                    headers = "",
                    body = patientAndReferralsJSON
                )
            )
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }

    /**
     * Transforms a Referral into a Cradle Server SMS API compatible JSON String.
     * @param referral
     * @return A JSON formatted String, else empty String if an Exception is caught.
     */
    fun processReferralToJSON(referral: Referral): String {
        try {
            val referralJSON = JacksonMapper.createWriter<Referral>().writeValueAsString(
                referral
            )
            val url = Uri.parse(urlManager.postReferral)
            val endpoint = url.path ?: throw Exception("URL path is null")
            return JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = endpoint,
                    headers = "",
                    body = referralJSON
                )
            )
        } catch (e: Exception) {
            Error(e.message, e.cause)
            return ""
        }
    }
}
