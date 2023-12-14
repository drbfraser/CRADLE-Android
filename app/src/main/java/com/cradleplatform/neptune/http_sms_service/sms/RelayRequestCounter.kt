package com.cradleplatform.neptune.http_sms_service.sms

import android.content.Context

/**
 * This global object is used to persist the number of SMS Relay Requests. This is necessary for
 * the 6 digit in the SMS header. Relay app needs this number to be different each time a relay
 * starts to display UI properly.
 *
 * In the documentations, it was discussed that a timestamp was not chosen to be used because it
 * would use up limited character space in each SMS.
 *
 */
object RelayRequestCounter {
    // TODO: It only uses 6 digits. What happens to Relay App UI when it overflows back to 0?
    private var count : Long = 0
        set(value) {
            field = if (value > 999999) 0 else value
        }

    private const val PREFS_NAME = "RelayRequestPrefs"
    private const val COUNTER_KEY = "counter"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        count = prefs.getLong(COUNTER_KEY, 0)
    }

    fun getCount(): Long {
        return count
    }

    fun incrementCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(COUNTER_KEY, ++count).apply()
    }
}