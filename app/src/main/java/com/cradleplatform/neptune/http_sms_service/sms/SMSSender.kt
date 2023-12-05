package com.cradleplatform.neptune.http_sms_service.sms

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.telephony.SmsManager
import com.cradleplatform.neptune.R
import android.widget.Toast
import androidx.core.content.edit
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.PatientAndReadings
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.SmsReadingWithReferral
import com.cradleplatform.neptune.utilities.SMSFormatter
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.listToString
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.stringToList
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.view.PatientReferralActivity
import com.cradleplatform.neptune.view.ReadingActivity
import com.cradleplatform.neptune.viewmodel.UserViewModel

import java.lang.Exception

class SMSSender(
    private val sharedPreferences: SharedPreferences,
    private val context: Context
) {
    fun sendPatientAndReadings(patientAndReadings: PatientAndReadings) {
        // TODO: Add target API endpoint information needed by the backend to json ??
        // TODO: requestNumber=0 as it is not implemented in the backend yet
        if (patientAndReadings.patient.lastServerUpdate == null) {
            val patientAndReadingsJSON = JacksonMapper.createWriter<PatientAndReadings>().writeValueAsString(
                patientAndReadings
            )
            val json = JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                SmsReadingWithReferral(
                    requestNumber = "0",
                    method = "POST",
                    endpoint = "api/patients",
                    headers = "",
                    body = patientAndReadingsJSON
                )
            )
            sendSmsMessageWithJson(json)
        } else {
            for (reading in patientAndReadings.readings) {
                val readingsJSON = JacksonMapper.createWriter<Reading>().writeValueAsString(
                    reading)
                val json = JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
                    SmsReadingWithReferral(
                        requestNumber = "0",
                        method = "POST",
                        endpoint = "api/readings",
                        headers = "",
                        body = readingsJSON
                    )
                )
                sendSmsMessageWithJson(json)
            }
        }
    }
    fun sendPatientAndReferral(patientAndReferrals: PatientAndReferrals) {
        val patientAndReferralsJSON = JacksonMapper.createWriter<PatientAndReferrals>()
            .writeValueAsString(patientAndReferrals)

        val json = JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
            SmsReadingWithReferral(
                requestNumber = "0",
                method = "POST",
                // TODO: Remove the hardcoded API endpoint
                endpoint = "api/patients",
                headers = "",
                body = patientAndReferralsJSON
            )
        )
        sendSmsMessageWithJson(json)
    }
    fun sendReferral(referral: Referral) {
        // TODO: Add target API endpoint information needed by the backend to json ??
        // TODO: requestNumber=0 as it is not implemented in the backend yet
        val referralJSON = JacksonMapper.createWriter<Referral>().writeValueAsString(
            referral
        )

        val json = JacksonMapper.createWriter<SmsReadingWithReferral>().writeValueAsString(
            SmsReadingWithReferral(
                requestNumber = "0",
                method = "POST",
                // TODO: Remove the hardcoded API endpoint
                endpoint = "api/referrals",
                headers = "",
                body = referralJSON
            )
        )
        sendSmsMessageWithJson(json)
    }
    private fun sendSmsMessageWithJson(data: String) {
        // Retrieve the encrypted secret key for the current user
        val smsKeyManager = SmsKeyManager(context)
        val smsSecretKey = smsKeyManager.retrieveSmsKey()
            ?: // TODO: handle the case when the secret key is not available
            error("Encryption failed - no valid smsSecretKey is available")

        val encodedMsg = SMSFormatter.encodeMsg(data, smsSecretKey)

        val smsRelayRequestCounter = sharedPreferences.getLong(context.getString(R.string.sms_relay_request_counter), 0)

        val msgInPackets =
            SMSFormatter.listToString(SMSFormatter.formatSMS(encodedMsg, smsRelayRequestCounter))
        sharedPreferences.edit(commit = true) {
            putString(context.getString(R.string.sms_relay_list_key), msgInPackets)
            putLong(context.getString(R.string.sms_relay_request_counter), smsRelayRequestCounter + 1)
        }

        sendSmsMessage(false)

        // Since this function is being called by threads other than the main thread, we need to
        // prepare looper to show toasts
        if (Looper.myLooper() == null) {
            Looper.prepare()
            Toast.makeText(context, context.getString(R.string.sms_sender_send),
                Toast.LENGTH_LONG).show()
            Looper.loop()
        } else {
            Toast.makeText(context, context.getString(R.string.sms_sender_send),
                Toast.LENGTH_LONG).show()
        }
    }

    fun sendSmsMessage(acknowledged: Boolean) {
        val smsRelayContentKey = context.getString(R.string.sms_relay_list_key)
        val smsRelayContent = sharedPreferences.getString(smsRelayContentKey, null)
        val relayPhoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
        val smsManager: SmsManager = SmsManager.getDefault()
        // Variable checks if we prepared a looper for the current thread or if it already existed
        var looperPrepared = false

        // Since this function is being called by both the main and other threads, we need to
        // prepare looper for the other threads in order to show toasts
        if (Looper.myLooper() == null) {
            Looper.prepare()
            looperPrepared = true
        }

        if (!smsRelayContent.isNullOrEmpty()) {
            val smsRelayMsgList = stringToList(smsRelayContent)

            // if acknowledgement received, remove window block and proceed to next
            if (acknowledged) {
                smsRelayMsgList.removeAt(0)
                if (smsRelayMsgList.isEmpty()) {
                    val finishedMsg = context.getString(R.string.sms_all_sent)
                    Toast.makeText(
                        context, finishedMsg,
                        Toast.LENGTH_LONG
                    ).show()
                    if (context is ReadingActivity || context is PatientReferralActivity) {
                        (context as Activity).finish()
                    }
                    if (looperPrepared) {
                        Looper.loop()
                    }
                    return
                }
            }

            try {
                val packetMsg = smsRelayMsgList.first()
                val packetMsgDivided = smsManager.divideMessage(packetMsg)

                // TODO: Discuss with Dr. Brian about using the sendMultiPartTextMessage
                // method as it is API 30+ only
                // TODO: change phone number - CHANGE this needs to be the destination phone number

                smsManager.sendMultipartTextMessage(
                    relayPhoneNumber, UserViewModel.USER_PHONE_NUMBER,
                    packetMsgDivided, null, null
                )
                Toast.makeText(
                    context, context.getString(R.string.sms_packet_sent),
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                // TODO: Fix the error here ==> java.lang.NullPointerException:
                // Can't toast on a thread that has not called Looper.prepare() when sending
                // just a referral for a patient
                Toast.makeText(
                    context, ex.message.toString(),
                    Toast.LENGTH_LONG
                ).show()
            }

            sharedPreferences.edit(commit = true) {
                putString(smsRelayContentKey, listToString(smsRelayMsgList))
            }
            if (looperPrepared) {
                Looper.loop()
            }
        }
    }
}
