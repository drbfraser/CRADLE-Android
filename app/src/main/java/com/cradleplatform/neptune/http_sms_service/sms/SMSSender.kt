package com.cradleplatform.neptune.http_sms_service.sms

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.http_sms_service.sms.SMSFormatter.Companion.encodeMsg
import com.cradleplatform.neptune.http_sms_service.sms.SMSFormatter.Companion.formatSMS
import com.cradleplatform.neptune.viewmodel.UserViewModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSSender @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
    private val sharedPreferences: SharedPreferences,
    private val appContext: Context,
    private val smsStateReporter: SmsStateReporter,
) {
    private var smsRelayQueue = ArrayDeque<String>()
    var showDialog = true
    var data = ""
    fun queueRelayContent(unencryptedData: String): Boolean {
        data = String(unencryptedData.toCharArray())
        val smsKey = smsKeyManager.retrieveSmsKey() ?: return false
        val encryptedData = encodeMsg(unencryptedData, smsKey.key)
        val smsPacketList = formatSMS(encryptedData, RelayRequestCounter.getCount())
        RelayRequestCounter.incrementCount(appContext)
        smsStateReporter.setSmsSender(this)
        smsStateReporter.initSending(smsPacketList.size)
        return smsRelayQueue.addAll(smsPacketList)
    }

    fun sendSmsMessage(acknowledged: Boolean) {
        val relayPhoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
        val smsManager: SmsManager = SmsManager.getDefault()
        smsStateReporter.state.postValue(SmsTransmissionStates.SENDING_TO_RELAY_SERVER)
        if (!smsRelayQueue.isNullOrEmpty()) {
            // if acknowledgement received, remove window block and proceed to next
            if (acknowledged) {
                smsRelayQueue.removeFirst()
                if (smsRelayQueue.isEmpty()) {
                    val finishedMsg = appContext.getString(R.string.sms_all_sent)
                    smsStateReporter.state.postValue(
                        SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            appContext, finishedMsg,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }
            }

            try {
                val packetMsg = smsRelayQueue.first()
                val packetMsgDivided = smsManager.divideMessage(packetMsg)

                // TODO: Discuss with Dr. Brian about using the sendMultiPartTextMessage
                // method as it is API 30+ only
                // TODO: change phone number - CHANGE this needs to be the destination phone number
                // TODO: Add IntentFilters to get SMS Sent Result
                smsManager.sendMultipartTextMessage(
                    relayPhoneNumber, UserViewModel.USER_PHONE_NUMBER,
                    packetMsgDivided, null, null
                )
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        appContext, appContext.getString(R.string.sms_packet_sent),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (ex: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        appContext, ex.message.toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun sendAckMessage(requestIdentifier: String, ackNumber: Int, numFragments: Int) {
        var ackMessage = """
        01
        CRADLE
        $requestIdentifier
        ${String.format("%03d", ackNumber)}
        ACK
        """.trimIndent().replace("\n", "-")

        val smsManager: SmsManager = SmsManager.getDefault()
        val relayPhoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
        smsStateReporter.state.postValue(SmsTransmissionStates.RECEIVING_SERVER_RESPONSE)
        try {
            smsManager.sendMultipartTextMessage(
                relayPhoneNumber, UserViewModel.USER_PHONE_NUMBER,
                smsManager.divideMessage(ackMessage), null, null
            )
        } catch (ex: Exception) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    appContext, ex.message.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        if (ackNumber == numFragments - 1) {
            // TODO: Determine if it's better to exit Activity here or when nothing is left
            // in the relay list (see if (smsRelayMsgList.isEmpty()))
            val finishedMsg = appContext.getString(R.string.sms_all_sent)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    appContext, finishedMsg,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun changeShowDialog(bool: Boolean) {
        showDialog = bool
    }
    fun reset() {
        smsRelayQueue.clear()
        showDialog = true
    }
}
