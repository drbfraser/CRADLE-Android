package com.cradleVSA.neptune.manager

import android.util.Log
import com.cradleVSA.neptune.model.Settings
import javax.inject.Inject

// TODO: Lots of duplicate properties in here, need to clean them out

/**
 * Constructs the various URLs required for communicating with the server.
 */
class UrlManager @Inject constructor(val settings: Settings) {

    /**
     * Endpoint for authenticating the user.
     */
    val authentication: String
        get() = "$base/user/auth"

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
     * The base server URL.
     */
    internal val base: String
        get() {
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
    fun getUpdates(currTime: Long): String = "$base/sync/updates?since=$currTime"

    fun getPatientsSync(lastSyncTimestamp: Long): String =
        "$base/sync/patients?since=$lastSyncTimestamp"

    fun getReadingsSync(lastSyncTimestamp: Long): String =
        "$base/sync/readings?since=$lastSyncTimestamp"

    fun getUpdatesNew(): String = "$base/sync/updates"

    /**
     * get a single reading by id
     */
    fun getReadingById(id: String): String = "$base/readings/$id"

    /**
     * get a patient info only
     */
    fun getPatientInfoOnly(id: String): String = "$base/patients/$id/info"

    /**
     * get a assessment by id
     */
    fun getAssessmentById(id: String): String = "$base/assessments/$id"

    val userPatientAssociation = "$base/associations"

    companion object {
        private const val TAG = "UrlManager"
    }
}