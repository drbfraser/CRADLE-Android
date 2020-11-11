package com.cradle.neptune.manager

import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.NetworkException
import com.cradle.neptune.net.NetworkResult
import com.cradle.neptune.net.RestApi
import com.cradle.neptune.net.Success
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
            is Failure ->
                if (result.statusCode == HTTP_NOT_FOUND) {
                    false
                } else {
                    return result.cast()
                }
            is Success -> true
            is NetworkException -> return result.cast()
        }

        // If the patient exists we only need to upload the reading, if not
        // then we need to upload the whole patient as well.
        return if (patientExists) {
            restApi.postReading(reading).map { PatientAndReadings(patient, listOf(it)) }
        } else {
            restApi.postPatient(PatientAndReadings(patient, listOf(reading)))
        }
    }
}
