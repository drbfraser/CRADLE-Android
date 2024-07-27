package com.cradleplatform.neptune.http_sms_service.sms

import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import com.cradleplatform.neptune.viewmodel.UserViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSSync @Inject constructor(
    private val smsSender: SMSSender,
    private val smsStateReporter: SmsStateReporter,
    private val sharedPreferences: SharedPreferences,
    @ApplicationContext private val context: Context
) {
    private lateinit var smsReceiver: SMSReceiver

    private fun setupSMSReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        intentFilter.priority = Int.MAX_VALUE

        val phoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
            ?: error("invalid phone number")
        smsReceiver = SMSReceiver(smsSender, phoneNumber, smsStateReporter)
        context.registerReceiver(smsReceiver, intentFilter)
    }

    private fun teardownSMSReceiver() {
        if (smsReceiver != null) {
            context.unregisterReceiver(smsReceiver)
        }
    }
}
