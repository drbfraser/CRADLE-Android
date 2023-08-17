package com.cradleplatform.neptune.manager

import android.util.Log
import com.cradleplatform.neptune.model.Settings
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

// TODO: Lots of duplicate properties in here, need to clean them out (Refer to issue # 29)

/**
 * Constructs the various URLs required for communicating with the server.
 */
@Singleton
class UrlManager @Inject constructor(val settings: Settings) {

    /**
     * Endpoint for authenticating the user.
     */
    val authentication: String
        get() = "$base/user/auth"

    /**
     * Endpoint for submitting the form.
     */

    val uploadFormResponse: String
        get() = "$base/forms/responses"

    /**
     * Endpoint for retrieving health facility information.
     */
    val healthFacilities: String
        get() = "$base/facilities"

    /**
     * Endpoint for getting all patients managed by the current user.
     */
    val getAllPatients: String
        get() = "$base/mobile/patients"

    val getAllReadings: String
        get() = "$base/mobile/readings"

    val getAllReferrals: String
        get() = "$base/mobile/referrals"

    val getAllAssessments: String
        get() = "$base/mobile/assessments"

    /**
     * Endpoint for getting All FormTemplates and it's classifications as summaries
     */
    val getAllFormsAsSummary: String
        get() = "$base/forms/classifications/summary"

    /**
     * Endpoint for posting a new patient.
     */
    val postPatient: String
        get() = "$base/patients"

    /**
     * Endpoint for posting a new reading for an existing patient.
     */
    val postReading: String
        get() = "$base/readings"

    /**
     * Endpoint for posting a new referral for an existing patient.
     */
    val postReferral: String
        get() = "$base/referrals"

    /**
     * Endpoint for posting a new assessment for an existing patient.
     */
    val postAssessment: String
        get() = "$base/assessments"

    /**
     * Endpoint for posting a new pregnancy for an existing patient.
     */
    fun postPregnancy(patientId: String): String = "$base/patients/$patientId/pregnancies"

    /**
     * Endpoint for editing an existing pregnancy
     */
    fun putPregnancy(pregnancyId: String): String = "$base/pregnancies/$pregnancyId"



    /**
     * Endpoint for getting secret key
     */
    fun getSmsKey(userID: Int): String = "$base/user/$userID/smsKey"

    /**
     * The base server URL.
     */
    internal val base: String
        get() {
            // change both of these to http when testing on dev server
            val protocol = if (settings.networkUseHttps) {
                "https://"
            } else {
                "http://"
            }

            val hostname = settings.networkHostname
            if (hostname == null) {
                Log.wtf(TAG, "Network hostname was null")
                throw NullPointerException()
            }

            val port = if (settings.networkPort.isNullOrBlank()) {
                ""
            } else {
                ":" + settings.networkPort
            }

            return "$protocol$hostname$port/api"
        }

    /**
     * Endpoint for getting all information about a single patient including all
     * of its readings and other components.
     *
     * @param id a patient id
     */
    fun getPatient(id: String) = "$base/patients/$id"

    /**
     * Endpoint for getting just the demographic information about a single
     * patient, no readings or other components.
     *
     * @param id a patient id
     */
    fun getPatientInfo(id: String) = "$base/patients/$id/info"

    /**
     * Endpoint for getting just the readings for a single patient.
     *
     * @param id a patient id
     */
    fun getPatientReadings(id: String) = "$base/patients/$id/readings"

    /**
     * Endpoint for getting a single reading.
     *
     * @param id a reading id
     */
    fun getReading(id: String) = "$base/readings/$id"

    /**
     * Search the database for a list of patient by Id or Initials
     * /patient/global/<String>
     */
    fun getGlobalPatientSearch(query: String) = "$base/patient/global/$query"

    /**
     * provides all the updates related to the user since the [currTime] stamp
     */
    @Deprecated("use new sync function", ReplaceWith("getUpdatesNew()"))
    fun getUpdates(currTime: BigInteger): String = "$base/sync/updates?since=$currTime"

    fun getPatientsSync(lastSyncTimestamp: BigInteger): String =
        "$base/sync/patients?since=$lastSyncTimestamp"

    fun getReadingsSync(lastSyncTimestamp: BigInteger): String =
        "$base/sync/readings?since=$lastSyncTimestamp"

    fun getReferralsSync(lastSyncTimestamp: BigInteger): String =
        "$base/sync/referrals?since=$lastSyncTimestamp"

    fun getAssessmentsSync(lastSyncTimestamp: BigInteger): String =
        "$base/sync/assessments?since=$lastSyncTimestamp"

    fun getUpdatesNew(): String = "$base/sync/updates"

    /**
     * get a single reading by id
     */
    fun getReadingById(id: String): String = "$base/readings/$id"

    /**
     * Get statistics for a given facility ID between two dates
     */
    fun getStatisticsForFacilityBetween(
        date1: BigInteger,
        date2: BigInteger,
        filterFacility: String
    ): String =
        "$base/stats/facility/$filterFacility?from=$date1&to=$date2"

    /**
     * Get statistics for a given user ID between two dates
     */
    fun getStatisticsForUserBetween(date1: BigInteger, date2: BigInteger, filterUser: Int): String =
        "$base/stats/user/$filterUser?from=$date1&to=$date2"

    /**
     * Get statistics for all users/facilities between two dates
     */
    fun getAllStatisticsBetween(date1: BigInteger, date2: BigInteger): String =
        "$base/stats/all?from=$date1&to=$date2"

    /**
     * get a patient info only
     */
    fun getPatientInfoOnly(id: String): String = "$base/patients/$id/info"

    /**
     * Create a new medical/drug record
     */
    fun postMedicalRecord(id: String): String = "$base/patients/$id/medical_records"

    /**
     * get a assessment by id
     */
    fun getAssessmentById(id: String): String = "$base/assessments/$id"

    /**
     * Endpoint for posting a new phone number for a user.
     *
     * @param id user id
     */
    fun postUserPhoneNumber(id: Int) = "$base/user/$id/phone"

    /**
     * Endpoint for getting a list of all relay phone numbers.
     *
     */
    fun getAllRelayPhoneNumbers() = "$base/phone/relays"

    val userPatientAssociation: String
        get() = "$base/patientAssociations"

    companion object {
        private const val TAG = "UrlManager"
    }
}
