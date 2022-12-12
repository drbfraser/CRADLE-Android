package com.cradleplatform.neptune.http_sms_service.http

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * This service is used to bridge the HTTP and SMS protocols.
 * It works as a standalone class and all HTTP / SMS logic is handled here.
 *
 * The Idea is (Activity) -> (ViewModel) -> This Service -> RestApi.kt
 *
 * All other classes (ViewModels) should only interact with this class for any HTTP / SMS related tasks.
 * Then this class will deal with the RestApi.kt / (optionally a separate class for SMS management)
 *
 * TODO: This class needs to be the refactor point for all other activities that use the HTTP / SMS protocols.
 * Currently, all classes have their own
 * implementation of the HTTP / SMS protocols
 * that communicate directly with the restApi
 * which is not ideal. This class should be
 * the only class that communicates
 * with the restApi and SMSManager classes.
 *
 * There is also a component of the database idea that exists separately
 * from the backend in a Room Database. It saves the data
 * in the phone memory for when the phone is offline. Its singular
 * implementation has to be implemented elsewhere
 * .
 */

enum class Protocol {
    HTTP,
    SMS
}

sealed class DatabaseObject {
    data class PatientWrapper(val patient: Patient) : DatabaseObject()
    data class ReferralWrapper(
        val patient: Patient,
        val referral: Referral,
        //val smsDataIfNeeded : String,
        val smsSender: SMSSender,
        val submissionMode: Protocol
    ) : DatabaseObject()
    data class ReadingWrapper(val reading: Reading, val submissionMode: Protocol) : DatabaseObject()
    data class FormResponseWrapper(val formResponse: FormResponse, val submissionMode: Protocol) : DatabaseObject()
}

class HttpSmsService @Inject constructor(private val restApi: RestApi) {
    suspend fun upload(databaseObject: DatabaseObject) {
        when (databaseObject) {
            //is DatabaseObject.Patient -> uploadPatient(databaseObject.patient)
            //is Reading -> uploadReading(databaseObject.referral)
            is DatabaseObject.ReferralWrapper -> uploadReferral(databaseObject)
            is DatabaseObject.FormResponseWrapper -> uploadForm(databaseObject)
            else -> {
                Log.d("HttpSmsService", "Error: Unknown database object type")
            }
        }
    }

    private suspend fun uploadReferral(referralWrapper: DatabaseObject.ReferralWrapper):
        NetworkResult<PatientAndReferrals> {
        when (referralWrapper.submissionMode) {
            Protocol.HTTP -> {
                // A copy of this code is in ReferralUploadManager.kt (the second function),
                // but we need to modularize the code
                // so everything must be refactored in here.
                val patientExists = when (val result = restApi.getPatientInfo(referralWrapper.patient.id)) {
                    is NetworkResult.Failure ->
                        if (result.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                            false
                        } else {
                            return result.cast()
                        }
                    is NetworkResult.Success -> true
                    is NetworkResult.NetworkException -> return result.cast()
                }

                // If the patient exists we only need to upload the reading, if not
                // then we need to upload the whole patient as well.
                return if (patientExists) {
                    restApi.postReferral(referralWrapper.referral).map {
                        PatientAndReferrals(referralWrapper.patient, listOf(it))
                    }
                } else {
                    restApi.postPatient(PatientAndReferrals(referralWrapper.patient, listOf(referralWrapper.referral)))
                }
            }
            Protocol.SMS -> {
                referralWrapper.smsSender.sendSmsMessage(false)
            }
        }
        //TODO: Placeholder return statement, return errors more gracefully, remove the dependency for JacksonMapper
        return NetworkResult.Failure(
            JacksonMapper
                .writerForReferral
                .writeValueAsBytes(referralWrapper.referral),
            HttpURLConnection.HTTP_INTERNAL_ERROR
        )
    }

    private suspend fun uploadForm(formResponseWrapper: DatabaseObject.FormResponseWrapper) {
        when (formResponseWrapper.submissionMode) {
            Protocol.HTTP -> {
                when (restApi.postFormResponse(formResponseWrapper.formResponse)) {
                    is NetworkResult.Success -> Log.d("HTTP_SMS_BRIDGE", "Form uploaded successfully")
                    is NetworkResult.Failure -> Log.d("HTTP_SMS_BRIDGE", "Form upload failed")
                    is NetworkResult.NetworkException -> Log.d("HTTP_SMS_BRIDGE", "Form upload failed")
                }
            }
            Protocol.SMS -> {
            }
        }
    }
}
