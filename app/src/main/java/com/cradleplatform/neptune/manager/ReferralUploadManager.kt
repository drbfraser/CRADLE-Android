package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.viewmodel.patients.ReferralFlowSaveResult
import java.net.HttpURLConnection
import javax.inject.Inject

/**
 * Manages uploading Referrals to the Cradle-Platform server. Either over HTTP or SMS.
 */
class ReferralUploadManager @Inject constructor(
    private val restApi: RestApi,
    private val referralManager: ReferralManager, // For the internal database.
    private val patientManager: PatientManager
) {

    suspend fun uploadReferral(
        referral: Referral,
        patient: Patient,
        protocol: Protocol
    ): ReferralFlowSaveResult {
        // Save Referral to local database.
        referralManager.addReferral(referral, false)

        /* First, check if the patient exists on the Cradle-Platform server, as the patient may
        * have been created locally and not yet uploaded.
        * Or, the patient may have been deleted on the server? But in cases where the patient has
        * been deleted on the server, we probably want the referral upload to fail, rather than
        * recreating a patient that was deleted on the server. This would be in keeping with the
        * strategy of deferring to the server.
        * */

        /* Before, to check if the patient exists on the server, a GET request was made to the
         * server. A much simpler way of checking would be to check the `lastServerUpdate` field
         * of the patient. If the field is `null`, then the patient was created locally and hasn't
         * yet been uploaded to the server.
         */
        val patientExistsOnServer = patient.lastServerUpdate != null

        /* If the patient exists on the server, we only need to upload the referral.
         * Otherwise, we need to upload the patient with the referral.
         * */
        if (patientExistsOnServer) {
            val result = restApi.postReferral(referral, protocol)
            return when (result) {
                is NetworkResult.Success -> {
                    updatePatientLastEdited(patient)
                    ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded
                }
                else -> ReferralFlowSaveResult.ErrorUploadingReferral
            }
        }
        else {
            val result = restApi.postPatient(PatientAndReferrals(patient, listOf(referral)), protocol)
            return when (result) {
                is NetworkResult.Success -> {
                    updatePatientLastEdited(patient)
                    ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded
                }
                else -> ReferralFlowSaveResult.ErrorUploadingReferral
            }
        }

    }

    suspend fun updatePatientLastEdited(patient: Patient) {
        patient.lastServerUpdate = patient.lastEdited
        patientManager.add(patient)
    }

}