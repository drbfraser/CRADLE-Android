package com.cradleplatform.neptune.viewmodel.HTTP_SMS_Bridge

import android.util.Log
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.net.map
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * This service is used to bridge the HTTP and SMS protocols.
 * It works as a standalone class and all HTTP / SMS logic is handled here.
 * All other classes should only interact with this class for any HTTP / SMS related tasks.
 * Then this class will deal with the RestApi.kt and SMSManager.kt classes.
 * TODO: This class needs to be the refactor point for all other activities that use the HTTP / SMS protocols.
 * Currently, all classes have their own implementation of the HTTP / SMS protocols that communicate directly with the restApi
 * which is not ideal. This class should be the only class that communicates with the restApi and SMSManager classes.
 *
 *
 * There is also a component of the database idea that exists separately from the backend in a Room Database. It saves the data
 * in the phone memory for when the phone is offline. Its singular implementation has to be implemented elsewhere.
 */

sealed class DatabaseObject{
    data class Patient(val patient: Patient) : DatabaseObject()
    data class Referral(val referral: Referral) : DatabaseObject()
    //data class Reading(val reading: Reading) : DatabaseObject()
    data class FormResponseWrapper(val formResponse: FormResponse) : DatabaseObject()
}

class HttpSmsService @Inject constructor(private val restApi: RestApi) {
    suspend fun upload(databaseObject: DatabaseObject) {
        when (databaseObject) {
            //is DatabaseObject.Patient -> uploadPatient(databaseObject.patient)
            //is Reading -> uploadReading(databaseObject.referral)
            is DatabaseObject.FormResponseWrapper -> uploadForm(databaseObject.formResponse)
            else -> {
                Log.d("HttpSmsService", "Error: Unknown database object type")
            }
        }
    }

    private suspend fun uploadForm(formResponse: FormResponse) {
        when (restApi.postFormResponse(formResponse)) {
            is NetworkResult.Success -> Log.d("HTTP_SMS_BRIDGE", "Form uploaded successfully")
            is NetworkResult.Failure -> Log.d("HTTP_SMS_BRIDGE", "Form upload failed")
            is NetworkResult.NetworkException -> Log.d("HTTP_SMS_BRIDGE", "Form upload failed")
        }
    }
}



/*

    /**
     * Manages uploading referrals via HTTP.
     */
    class ReferralUploadManager @Inject constructor(private val restApi: RestApi) {

        /**
         * Attempts to upload an independent referral to the server, without a reading.
         *
         * As referral has been detached from a mandatory reading association,
         * we can now upload a referral independent of any reading.
         *
         * @param patient the patient being referred
         * @param referral the referral
         * @return result of the network request. [patient] is returned back if [patient] already exists
         * on the server.
         */
        suspend fun uploadReferralViaWeb(
            patient: Patient,
            referral: Referral
        ): NetworkResult<PatientAndReferrals> {
            // First check to see if the patient exists. We don't have an explicit
            // API for this so we use the response code of the get patient info
            // API to determine whether the patient exists or not.
            val patientExists = when (val result = restApi.getPatientInfo(patient.id)) {
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
                restApi.postReferral(referral).map { PatientAndReferrals(patient, listOf(it)) }
            } else {
                restApi.postPatient(PatientAndReferrals(patient, listOf(referral)))
            }
        }
    }

}

 */