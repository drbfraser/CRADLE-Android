package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Might want to encode the acknowledgment in the future
private val ackRegexPattern = Regex("^01-CRADLE-\\d{6}-\\d{3}-ACK$")

@AndroidEntryPoint
class SMSReceiver @Inject constructor(
    private val smsSender: SMSSender,
    private val relayPhoneNumber: String,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val pdus = data?.get("pdus") as Array<*>

        for (element in pdus) {
            // if smsMessage is null, we continue to the next one
            val smsMessage = SmsMessage.createFromPdu(element as ByteArray?) ?: continue
            val messageBody = smsMessage.messageBody
            if (smsMessage.originatingAddress.equals(relayPhoneNumber) && messageBody.matches(
                    ackRegexPattern
                )
            ) {
                smsSender.sendSmsMessage(true)
            }
        }
    }
}
