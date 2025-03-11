package com.cradleplatform.neptune.http_sms_service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.telephony.SmsMessage
import android.util.Log
import com.cradleplatform.neptune.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// TODO: Use shared prefs or other methods instead of these and decrypt data in an activity
//  Note that this data is not being read anywhere and is only being stored here
/*   (Why would we do the decryption in an activity?) */
@AndroidEntryPoint
class SMSReceiver @Inject constructor(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val smsSender: SMSSender,
    private val smsStateReporter: SmsStateReporter,
) : BroadcastReceiver() {

    // Get relayPhoneNumber
    private lateinit var relayPhoneNumber: String

    private var requestIdentifier = ""
    private var relayData = ""
    private var isError: Boolean? = null
    private var numberReceivedMessages = 0
    private var totalMessages = 0
    private var errorCode: Int? = null

    private var smsFormatter: SMSFormatter = SMSFormatter()

    private val intentFilter = IntentFilter()

    init {
        intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        intentFilter.priority = Int.MAX_VALUE
    }

    /**
     * Start listening for SMS messages.
     * This method should only be called immediately before beginning an SMS Transmission.
     * Each call to this method should be matched with a corresponding call to [unregister].
     */
    fun register() {
        /* Update relayPhoneNumber each time, in case it has been changed. */
        relayPhoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
            ?: error("Invalid phone number")
        /* Reset variables, in case the Receiver was unregistered before finishing
         * for some reason. */
        reset()
        context.registerReceiver(this, intentFilter)
    }

    /**
     * Stop listening for SMS messages.
     */
    fun unregister() {
        context.unregisterReceiver(this)
    }

    private fun reset() {
        requestIdentifier = ""
        totalMessages = 0
        numberReceivedMessages = 0
        relayData = ""
        isError = null
        errorCode = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val data = intent?.extras
        val pdus = data?.get("pdus") as Array<*>

        for (element in pdus) {
            val smsMessage = SmsMessage.createFromPdu(element as ByteArray?) ?: continue

            val isMessageFromRelayPhone = smsMessage.originatingAddress.equals(relayPhoneNumber)
            if (!isMessageFromRelayPhone) {
                continue
            }

            val messageBody = smsMessage.messageBody

            // send next part of the message when ACK is received
            if (smsFormatter.isAckMessage(messageBody)) {
                smsSender.sendSmsMessage(true)
                smsStateReporter.incrementSent()
            }
            // start storing message data and send ACK message
            else if (smsFormatter.isFirstReplyMessage(messageBody)) {
                isError = smsFormatter.isFirstReplyError(messageBody)
                if (isError == true) {
                    errorCode = smsFormatter.getErrorCode(messageBody)
                }

                requestIdentifier = smsFormatter.getRequestIdentifier(messageBody)
                relayData = smsFormatter.getFirstMessageString(messageBody)

                smsFormatter.getTotalNumMessages(messageBody).let {
                    totalMessages = it
                    smsStateReporter.initReceiving(it)
                }

                numberReceivedMessages = 1
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
                    numberReceivedMessages < totalMessages
                ) {
                    numberReceivedMessages += 1
                    smsStateReporter.incrementReceived()
                    smsStateReporter.retry.postValue(false)
                    relayData += smsFormatter.getRestMessageString(messageBody)
                    smsSender.sendAckMessage(
                        requestIdentifier,
                        numberReceivedMessages - 1,
                        totalMessages
                    )
                }
                check()
            }
        }
    }

    //TODO remove this function when data is being read in an activity
    // ^(What did they mean by this?)
    private fun check() {
        // this happens at the end of exchange
        // resetting vars if process finished
        if (numberReceivedMessages == totalMessages) {
            smsStateReporter.handleResponse(relayData, errorCode)
            Log.d("Search: Total Messages received", numberReceivedMessages.toString())
            smsSender.reset()
            reset()
            smsStateReporter.resetStateReporter()
        }
    }
}
