package com.cradle.neptune.manager

import android.util.Log
import com.cradle.neptune.model.SettingsNew
import javax.inject.Inject

/**
 * Constructs the various URLs required for communicating with the server.
 */
class UrlManager @Inject constructor(val settings: SettingsNew) {

    /**
     * Endpoint for authenticating the user.
     */
    val authentication: String
        get() = "$base/user/auth"

    /**
     * Endpoint for retrieving information about all patients.
     */
    val allPatientInfo: String
        get() = "$base/patient/allinfo"

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
        get() = "$base/summarized/follow_up"

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

            val hostname = settings.networkHostname.also {
                if (it == null) {
                    val msg = "Network hostname was null"
                    Log.wtf(this::class.simpleName, msg)
                    throw NullPointerException(msg)
                }
            }

            val port = if (settings.networkPort != null) {
                ":" + settings.networkPort
            } else {
                ""
            }

            return protocol + hostname + port
        }
}
