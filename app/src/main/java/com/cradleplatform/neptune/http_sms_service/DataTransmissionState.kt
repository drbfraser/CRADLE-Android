package com.cradleplatform.neptune.http_sms_service

import android.content.Context

// This could be a boolean, but to future proof for more transmission
enum class TransmissionStatus {
    TRANSMITTING,
    IDLE
}

object DataTransmissionState {
    private var status: TransmissionStatus = TransmissionStatus.IDLE

    private const val PREFS_NAME = "DataTransmissionPrefs"
    private const val STATUS_KEY = "status"

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        status = prefs.getString(STATUS_KEY, null)?.let {
            TransmissionStatus.valueOf(it)
        } ?: TransmissionStatus.IDLE
    }

    fun getStatus(): TransmissionStatus {
        return status
    }

    fun setStatus(context: Context, transmissionStatus: TransmissionStatus) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(STATUS_KEY, transmissionStatus.name).apply()
        status = transmissionStatus
    }
}
