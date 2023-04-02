package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage

// Might want to encode the acknowledgment in the future
private val ACK_REGEX_PATTERN = Regex("^01-CRADLE-\\d{6}-\\d{3}-ACK$")

class SMSReceiver(private val smsSender: SMSSender, private val relayPhoneNumber: String) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val pdus = data?.get("pdus") as Array<*>

        for (element in pdus) {
            // if smsMessage is null, we continue to the next one
            val smsMessage = SmsMessage.createFromPdu(element as ByteArray?) ?: continue
            val messageBody = smsMessage.messageBody
            if (smsMessage.originatingAddress.equals(relayPhoneNumber) && messageBody.matches(
                    ACK_REGEX_PATTERN
                )
            ) {
                smsSender.sendSmsMessage(true)
            }
        }
    }
}
