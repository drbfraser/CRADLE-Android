package com.cradleplatform.neptune.utilities.sms

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import com.cradleplatform.neptune.R
import android.widget.Toast
import androidx.core.content.edit
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.listToString
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.stringToList
import com.cradleplatform.neptune.view.PatientReferralActivity
import com.cradleplatform.neptune.view.ReadingActivity

import java.lang.Exception

class SMSSender(
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) {

    fun sendSmsMessage(acknowledged: Boolean) {
        val smsRelayContentKey = context.getString(R.string.sms_relay_list_key)
        val smsRelayContent = sharedPreferences.getString(smsRelayContentKey, null)
        val phoneNumber = context.getString(R.string.relay_phone_number)
        val smsManager: SmsManager = SmsManager.getDefault()

        if (!smsRelayContent.isNullOrEmpty()) {
            val smsRelayMsgList = stringToList(smsRelayContent)

            // if acknowledgement received, remove window block and proceed to next
            if (acknowledged) {
                smsRelayMsgList.removeAt(0)
                if (smsRelayMsgList.isEmpty()) {
                    Toast.makeText(
                        context, context.getString(R.string.sms_all_sent),
                        Toast.LENGTH_LONG
                    ).show()
                    if (context is ReadingActivity || context is PatientReferralActivity) {
                        (context as Activity).finish()
                    }
                    return
                }
            }

            try {
                val packetMsg = smsRelayMsgList.first()
                val packetMsgDivided = smsManager.divideMessage(packetMsg)
                smsManager.sendMultipartTextMessage(phoneNumber, null, packetMsgDivided, null, null)
                Toast.makeText(
                    context, context.getString(R.string.sms_packet_sent),
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                Toast.makeText(
                    context, ex.message.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            sharedPreferences.edit(commit = true) {
                putString(smsRelayContentKey, listToString(smsRelayMsgList))
            }
        }
    }
}
