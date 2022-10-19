package com.cradleplatform.neptune.utilities.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage

// Might want to encode the acknowledgment in the future
private const val ACKNOWLEDGEMENT = "Received"

class SMSReceiver(private val smsSender: SMSSender, private val relayPhoneNumber: String) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val pdus = data?.get("pdus") as Array<*>

        for (element in pdus) {
            // if smsMessage is null, we continue to the next one
            val smsMessage = SmsMessage.createFromPdu(element as ByteArray?) ?: continue
            val messageBody = smsMessage.messageBody
            if (smsMessage.originatingAddress.equals(relayPhoneNumber) && messageBody.contentEquals(ACKNOWLEDGEMENT)) {
                smsSender.sendSmsMessage(true)
            }
        }
    }
}
