package com.cradleVSA.neptune.model

import android.content.Context
import android.content.SharedPreferences
import com.cradleVSA.neptune.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// Implementation note: this class and all it's properties are marked `open`
// so that we can mock them out for testing.

/**
 * Holds app-wide settings which are persisted in Android's shared preference.
 */
open class Settings @Inject constructor(
    val sharedPreferences: SharedPreferences,
    @ApplicationContext val context: Context
) {

    /* Network */

    /**
     * The network hostname as configured in Settings > Advanced.
     */
    open val networkHostname: String?
        get() = sharedPreferences.getString(
            context.getString(R.string.key_server_hostname),
            context.getString(R.string.settings_default_server_hostname)
        )

    /**
     * The network port as configured in Settings > Advanced.
     */
    open val networkPort: String?
        get() = sharedPreferences.getString(
            context.getString(R.string.key_server_port),
            null
        )

    /**
     * Whether to use HTTPS or not, configured in Settings > Advanced.
     */
    open val networkUseHttps: Boolean
        get() = sharedPreferences.getBoolean(
            context.getString(R.string.key_server_use_https),
            true
        )

    /* VHT Info */

    /**
     * The user's name as configured in Settings.
     */
    open val vhtName: String?
        get() = sharedPreferences.getString(
            context.getString(R.string.key_vht_name),
            null
        )

    /**
     * The user's region as configured in Settings.
     */
    open val region: String?
        get() = sharedPreferences.getString(
            context.getString(R.string.key_region),
            null
        )

    /* OCR */

    /**
     * Whether OCR is enabled or not, configured in Settings > Advanced.
     */
    open val isOcrEnabled: Boolean
        get() = sharedPreferences.getBoolean("setting_ocr_enabled", true)

    /**
     * Whether OCR debug options are enabled or not, configured in Settings > Advanced.
     */
    open val isOcrDebugEnabled: Boolean
        get() = sharedPreferences.getBoolean("setting_ocr_debug_enabled", false)

    /* Internal Metadata */

    /**
     * The last time follow up information was downloaded.
     */
    open var lastTimeFollowUpDownloaded: String?
        get() = sharedPreferences.getString("lastSavedTime", "No previous time")
        set(value) = sharedPreferences
            .edit()
            .putString("lastSavedTime", value)
            .apply()
}
