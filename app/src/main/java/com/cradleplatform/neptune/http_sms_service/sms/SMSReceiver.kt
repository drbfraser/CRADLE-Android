package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage

// Might want to encode the acknowledgment in the future
private val ackRegexPattern = Regex("^01-CRADLE-\\d{6}-\\d{3}-ACK$")
private val firstRegexPattern = Regex("^01-CRADLE-(\\d{6})-(\\d{3})-(.+)")
private val restRegexPattern = Regex("^(\\d{3})-(.+)")

class SMSReceiver(private val smsSender: SMSSender, private val relayPhoneNumber: String) : BroadcastReceiver() {

    private lateinit var requestIdentifier: String
    private lateinit var encryptedMessage: String
    private var numberReceivedMessages = 0
    private var totalMessages = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val messages = data?.get("pdus") as Array<*>

        for (message in messages) {
            // if smsMessage is null, we continue to the next one
            val smsMessage = SmsMessage.createFromPdu(message as ByteArray?) ?: continue
            val isMessageFromRelayPhone = smsMessage.originatingAddress.equals(relayPhoneNumber)
            val messageBody = smsMessage.messageBody
            if (isMessageFromRelayPhone && messageBody.matches(ackRegexPattern)
            ) {
                smsSender.sendSmsMessage(true)
            }
            else if (isMessageFromRelayPhone && messageBody.matches(firstRegexPattern)){
                requestIdentifier = getRequestIdentifier(messageBody)
                totalMessages = getTotalNumMessages(messageBody)
                encryptedMessage = getFirstMessageString(messageBody)
                numberReceivedMessages = 1
                smsSender.sendAckMessage(requestIdentifier, numberReceivedMessages)
            }
            else if (isMessageFromRelayPhone && messageBody.matches(restRegexPattern)){



            }
        }
    }

    private fun getRequestIdentifier(smsMessage: String): String {
        return firstRegexPattern.find(smsMessage)?.groupValues!![1]

    }
    private fun getTotalNumMessages(smsMessage: String): Int {
        return firstRegexPattern.find(smsMessage)?.groupValues!![2].toInt()
    }

    private fun getFirstMessageString(smsMessage: String): String{
        return firstRegexPattern.find(smsMessage)?.groupValues!![3]
    }

    private fun getMessageNumber(smsMessage: String): Int{
        return restRegexPattern.find(smsMessage)?.groupValues!![1].toInt()
    }

}
