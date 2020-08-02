package com.cradle.neptune.manager

import android.util.Log
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.PatientAndReadings
import com.cradle.neptune.model.Reading
import com.cradle.neptune.network.Api
import com.cradle.neptune.network.Failure
import javax.inject.Inject

private const val HTTP_NOT_FOUND = 404

class ReferralUploadManger @Inject constructor(private val api: Api) {

    suspend fun sendReferralViaWeb(patient: Patient, reading: Reading) {
        if (reading.referral == null) {
            throw IllegalArgumentException("reading must contain a nested referral")
        }

        // First check to see if the patient exists. We don't have an explicit
        // API
        val patientCheckResult = api.getPatientInfo(patient.id)
        val patientExists = if (patientCheckResult is Failure) {
            if (patientCheckResult.value.networkResponse.statusCode != HTTP_NOT_FOUND) {
                Log.e(this::class.simpleName, "Patient check failed with non 404 error, aborting upload")
                return
            }
            false
        } else {
            true
        }

        // If the patient exists we only need to upload the reading, if not
        // then we need to upload the whole patient as well.
        val uploadResult = if (patientExists) {
            api.postReading(reading)
        } else {
            api.postPatient(PatientAndReadings(patient, listOf(reading)))
        }
    }
}
