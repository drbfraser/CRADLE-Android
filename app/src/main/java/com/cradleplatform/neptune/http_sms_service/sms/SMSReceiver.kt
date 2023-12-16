package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.cradleplatform.neptune.utilities.SMSFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SMSReceiver @Inject constructor(
    private val smsSender: SMSSender,
    private val relayPhoneNumber: String,
    private val smsStateReporter: SmsStateReporter,
) : BroadcastReceiver() {

    private var smsFormatter: SMSFormatter = SMSFormatter()

    // TODO: Use shared prefs or other methods instead of these and decrypt data in an activity
    // Note that this data is not being read anywhere and is only being stored here
    private var requestIdentifier = ""
    private var encryptedMessage = ""
    private var numberReceivedMessages = 0
    private var totalMessages = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val pdus = data?.get("pdus") as Array<*>

        for (element in pdus) {
            // if smsMessage is null, we continue to the next one
            val smsMessage = SmsMessage.createFromPdu(element as ByteArray?) ?: continue
            val isMessageFromRelayPhone = smsMessage.originatingAddress.equals(relayPhoneNumber)

            // is message is not from relay phone continue to next one
            if (!isMessageFromRelayPhone) {
                continue
            }

            val messageBody = smsMessage.messageBody

            // send next part of the message when ACK is received
            if (smsFormatter.isAckMessage(messageBody)
            ) {
                smsSender.sendSmsMessage(true)
                smsStateReporter.incrementSent()
            }
            // start storing message data and send ACK message
            else if (smsFormatter.isFirstMessage(messageBody)) {

                requestIdentifier = smsFormatter.getRequestIdentifier(messageBody)
                totalMessages = smsFormatter.getTotalNumMessages(messageBody)
                smsStateReporter.initReceiving(totalMessages)
                encryptedMessage = smsFormatter.getFirstMessageString(messageBody)
                numberReceivedMessages = 1
                smsStateReporter.incrementReceived()
                smsSender.sendAckMessage(
                    requestIdentifier,
                    numberReceivedMessages - 1,
                    totalMessages
                )
            }
            // continue storing message data and send ACK message
            else if (smsFormatter.isRestMessage(messageBody)) {

                if (smsFormatter.getMessageNumber(messageBody) <= totalMessages &&
                    numberReceivedMessages < totalMessages) {
                    numberReceivedMessages += 1
                    smsStateReporter.incrementReceived()
                    val newPart = smsFormatter.getRestMessageString(messageBody)
                    encryptedMessage += newPart
                    smsSender.sendAckMessage(requestIdentifier, numberReceivedMessages - 1, totalMessages)
                }

                // this happens at the end of exchange
                // resetting vars if process finished
                if (numberReceivedMessages == totalMessages) {
                    Log.d("Search: Encrypted Message", encryptedMessage)
                    Log.d("Search: Total Messages received", numberReceivedMessages.toString())
                    smsStateReporter.decryptMessage(encryptedMessage)
                    requestIdentifier = ""
                    totalMessages = 0
                    numberReceivedMessages = 0
                    encryptedMessage = ""
                }
            }
        }
    }
}
