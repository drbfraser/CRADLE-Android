package com.cradleplatform.neptune.http_sms_service.sms

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.widget.Toast
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.encodeMsg
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.formatSMS
import com.cradleplatform.neptune.view.PatientReferralActivity
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.UserViewModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SMSSender @Inject constructor(
    private val smsKeyManager: SmsKeyManager,
    private val sharedPreferences: SharedPreferences,
    private val appContext: Context,
) {
    private var relayRequestCount: Long = 0
    private var smsRelayQueue = ArrayDeque<String>()
    private var smsSecretKey = smsKeyManager.retrieveSmsKey()
        ?: // TODO: handle the case when the secret key is not available
        error("Encryption failed - no valid smsSecretKey is available")
    // TODO: Remove this once State LiveData reporting is added
    // Activities based on a "Finished" State instead of from here.
    private var activityContext: Context? = null
    fun setActivityContext(activity: Context) {
        activityContext = activity
    }

    fun queueRelayContent(unencryptedData: String): Boolean {
        val encryptedData = encodeMsg(unencryptedData, smsSecretKey)
        val smsPacketList = formatSMS(encryptedData, relayRequestCount)
        return smsRelayQueue.addAll(smsPacketList)
    }

    fun sendSmsMessage(acknowledged: Boolean) {
        val relayPhoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
        val smsManager: SmsManager = SmsManager.getDefault()

        if (!smsRelayQueue.isNullOrEmpty()) {
            // if acknowledgement received, remove window block and proceed to next
            if (acknowledged) {
                smsRelayQueue.removeFirst()
                if (smsRelayQueue.isEmpty()) {
                    val finishedMsg = appContext.getString(R.string.sms_all_sent)
                    Toast.makeText(
                        appContext, finishedMsg,
                        Toast.LENGTH_LONG
                    ).show()
                    // TODO: Remove this after State reporting is implemented. Move logic to Activity instead.
                    if (activityContext is ReadingActivity || activityContext is PatientReferralActivity) {
                        (activityContext as Activity).finish()
                        activityContext = null
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

                smsManager.sendMultipartTextMessage(
                    relayPhoneNumber, UserViewModel.USER_PHONE_NUMBER,
                    packetMsgDivided, null, null
                )
                Toast.makeText(
                    appContext, appContext.getString(R.string.sms_packet_sent),
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                // TODO: Fix the error here ==> java.lang.NullPointerException:
                // Can't toast on a thread that has not called Looper.prepare() when sending
                // just a referral for a patient
                Toast.makeText(
                    appContext, ex.message.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
