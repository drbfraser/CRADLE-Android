package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.cradleplatform.neptune.utilities.SMSFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// TODO: Use shared prefs or other methods instead of these and decrypt data in an activity
// Note that this data is not being read anywhere and is only being stored here

@AndroidEntryPoint
class SMSReceiver @Inject constructor(
    private val smsSender: SMSSender,
    private val relayPhoneNumber: String,
    private val smsStateReporter: SmsStateReporter,
) : BroadcastReceiver() {

    private var requestIdentifier = ""
    private var relayData = ""
    private var isError: Boolean? = null
    private var numberReceivedMessages = 0
    private var totalMessages = 0
    private var errorCode: Int? = null

    private var smsFormatter: SMSFormatter = SMSFormatter()

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
            else if (smsFormatter.isFirstReplyMessage(messageBody)) {

                isError = smsFormatter.isFirstReplyError(messageBody)
                requestIdentifier = smsFormatter.getRequestIdentifier(messageBody)
                smsFormatter.getTotalNumMessages(messageBody)
                    .let {
                        totalMessages = it
                        smsStateReporter.initReceiving(it)
                    }
                relayData = smsFormatter.getFirstMessageString(messageBody)
                numberReceivedMessages = 1
                if (isError == true) {
                    errorCode = smsFormatter.getErrorCode(messageBody)
                }
                smsSender.sendAckMessage(
                    requestIdentifier,
                    numberReceivedMessages - 1,
                    totalMessages
                )
                check()
            }
            // continue storing message data and send ACK message
            else if (smsFormatter.isRestMessage(messageBody)) {

                if (smsFormatter.getMessageNumber(messageBody) <= totalMessages &&
                    numberReceivedMessages < totalMessages) {
                    numberReceivedMessages += 1
                    smsStateReporter.incrementReceived()
                    relayData += smsFormatter.getRestMessageString(messageBody)
                    smsSender.sendAckMessage(requestIdentifier, numberReceivedMessages - 1, totalMessages)
                }
                check()
            }
        }
    }
    
    fun reset() {
        smsSender.reset()
        requestIdentifier = ""
        totalMessages = 0
        numberReceivedMessages = 0
        relayData = ""
        isError = null
        errorCode = null
    }

    //TODO remove this function when data is being read in an activity
    private fun check() {
        // this happens at the end of exchange
        // resetting vars if process finished
        if (numberReceivedMessages == totalMessages) {
            smsStateReporter.handleResponse(relayData, errorCode)
            Log.d("Search: Encrypted Message/Error", "$isError  $relayData")
            Log.d("Search: Total Messages received", numberReceivedMessages.toString())
            reset()
        }
    }
}
