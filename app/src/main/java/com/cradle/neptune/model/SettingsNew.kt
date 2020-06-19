package com.cradle.neptune.model

import android.content.SharedPreferences
import javax.inject.Inject

// Implementation note: this class and all it's properties are marked `open`
// so that we can mock them out for testing.

/**
 * Holds app-wide settings which are persisted in Android's shared preference.
 */
open class SettingsNew @Inject constructor(val sharedPreferences: SharedPreferences) {
    /**
     * The server's hostname.
     */
    open val networkHostname: String?
        get() = sharedPreferences.getString("setting_server_hostname", null)

    /**
     * The port to connect through.
     */
    open val networkPort: String?
        get() = sharedPreferences.getString("setting_server_port", null)

    /**
     * Should we use HTTPS when connecting to the server?
     */
    open val networkUseHttps: Boolean
        get() = sharedPreferences.getBoolean("setting_server_use_https", true)
}
