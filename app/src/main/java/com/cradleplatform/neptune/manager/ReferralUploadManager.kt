package com.cradleplatform.neptune.manager

import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.net.RestApi
import com.cradleplatform.neptune.net.map
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import javax.inject.Inject

/**
 * Manages uploading referrals via HTTP.
 */
class ReferralUploadManager @Inject constructor(private val restApi: RestApi) {

    /**
     * Attempts to upload a referral to the server.
     *
     * The referral itself is actually nested within [reading], but we can't
     * just upload that to the server because the reading (and maybe the
     * patient) don't yet exist up there. This method takes care of figuring
     * out what additional data needs to be uploaded along with the referral.
     *
     * @param patient the patient being referred
     * @param reading the reading containing the referral
     * @throws IllegalArgumentException if [reading] does not contain a referral
     * @return result of the network request. [patient] is returned back if [patient] already exists
     * on the server.
     */
    suspend fun uploadReferralViaWeb(
        patient: Patient,
        reading: Reading
    ): NetworkResult<PatientAndReadings> {
        if (reading.referral == null) {
            error("reading must contain a nested referral")
        }

        // First check to see if the patient exists. We don't have an explicit
        // API for this so we use the response code of the get patient info
        // API to determine whether the patient exists or not.
        val patientExists = when (val result = restApi.getPatientInfo(patient.id)) {
            is NetworkResult.Failure ->
                if (result.statusCode == HTTP_NOT_FOUND) {
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
            restApi.postReading(reading).map { PatientAndReadings(patient, listOf(it)) }
        } else {
            restApi.postPatient(PatientAndReadings(patient, listOf(reading)))
        }
    }

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
    suspend fun uploadIndependentReferralViaWeb(
        patient: Patient,
        referral: Referral
    ): NetworkResult<PatientAndReferrals> {
        // First check to see if the patient exists. We don't have an explicit
        // API for this so we use the response code of the get patient info
        // API to determine whether the patient exists or not.
        val patientExists = when (val result = restApi.getPatientInfo(patient.id)) {
            is NetworkResult.Failure ->
                if (result.statusCode == HTTP_NOT_FOUND) {
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
