package com.cradle.neptune.manager

import android.util.Log
import com.cradle.neptune.model.Settings
import javax.inject.Inject

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
     * Endpoint for retrieving information about all patients.
     */
    val allPatientInfo: String
        get() = "$base/patients"

    /**
     * Endpoint for retrieving health facility information.
     */
    val healthFacility: String
        get() = "$base/health_facility"

    /**
     * Endpoint for retrieving information about a reading.
     */
    val reading: String
        get() = "$base/patient/reading"

    /**
     * Endpoint for retrieving referral information.
     */
    val referral: String
        get() = "$base/referral"

    /**
     * Endpoint for retrieving follow up information.
     */
    val followUp: String
        get() = "$base/mobile/summarized/follow_up"

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
                val msg = "Network hostname was null"
                Log.wtf(this::class.simpleName, msg)
                throw NullPointerException(msg)
            }

            val port = if (settings.networkPort.isNullOrBlank()) {
                ""
            } else {
                ":" + settings.networkPort
            }

            return "http://10.0.2.2:5000/api"
        }

    /**
     * Endpoint for retrieving all readings associated with a given patient id.
     */
    fun readingsForPatient(patientId: String) = "$base/patient/reading/$patientId"

    /**
     * Search the database for a list of patient by Id or Initials
     * /patient/global/<String>
     */
    fun getGlobalPatientSearch(query: String) = "$base/patient/global/$query"
}
