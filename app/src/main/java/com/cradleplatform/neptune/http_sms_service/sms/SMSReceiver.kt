package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage

// Might want to encode the acknowledgment in the future
private val ackRegexPattern = Regex("^01-CRADLE-\\d{6}-\\d{3}-ACK$")
private val firstRegexPattern = Regex("^01-CRADLE-(\\d{6})-(\\d{3})-(.+)")
private val restRegexPattern = Regex("^(\\d{3})-(.+)")

// TODO: Use shared prefs or other methods instead of these and decrypt data in an activity
// Note that this data is not being read anywhere and is only being stored here
private var requestIdentifier = ""
private var encryptedMessage = ""
private var numberReceivedMessages = 0
private var totalMessages = 0

class SMSReceiver(private val smsSender: SMSSender, private val relayPhoneNumber: String) : BroadcastReceiver() {

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

            // ack message logic assumes
            else if (isMessageFromRelayPhone && messageBody.matches(firstRegexPattern)){
                requestIdentifier = getRequestIdentifier(messageBody)
                totalMessages = getTotalNumMessages(messageBody)
                encryptedMessage = getFirstMessageString(messageBody)
                numberReceivedMessages = 1
                smsSender.sendAckMessage(requestIdentifier, numberReceivedMessages)
            }
            else if (isMessageFromRelayPhone && messageBody.matches(restRegexPattern)){

                if (getMessageNumber(messageBody) < totalMessages + 1 &&
                    numberReceivedMessages < totalMessages){

                    numberReceivedMessages += 1
                    encryptedMessage += getFirstMessageString(messageBody)
                    smsSender.sendAckMessage(requestIdentifier, numberReceivedMessages)
                }

                // resetting vars if process finished
                if(numberReceivedMessages == totalMessages){
                    requestIdentifier = ""
                    totalMessages = 0
                    numberReceivedMessages = 0
                    encryptedMessage = ""
                }
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

    private fun getRestMessageString(smsMessage: String): String{
        return restRegexPattern.find(smsMessage)?.groupValues!![1]
    }

}
