package com.cradleplatform.neptune.utilities

import android.util.Base64
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.google.firebase.crashlytics.internal.model.ImmutableList
import com.google.gson.Gson
import javax.crypto.SecretKey
import kotlin.math.min

enum class RelayAction {
    READING,
    REFERRAL,
    FORMRESPONSE
}

class SMSFormatter {
    companion object {
        /**
         * Send 2 packets at once through MultipartTextMessage.
         * Currently set to 2 as <= 320 chars is recommended by Twilio, a leading 3rd party SMS provider
         * Since the message is greater than 160 chars, only 153 can be used for contents.
         * First 7 are reserved for header.
         */
        const val PACKET_SIZE = 153 * 2
        private const val MAX_PACKET_NUMBER = 99


        // Http Header
        private const val SMS_TUNNEL_PROTOCOL_VERSION = "01"
        private const val MAGIC_STRING = "CRADLE"
        private const val FRAGMENT_HEADER_LENGTH = 3
        private const val REQUEST_NUMBER_LENGTH = 6

        fun encodeMsg(msg: String, action: RelayAction, secretKey: SecretKey): String {
            val formattedMsg = AESEncryptor.encrypt(GzipCompressor.compress(msg), secretKey)
            return Base64.encodeToString(formattedMsg, Base64.DEFAULT)
        }

        private fun computeRequestHeaderLength(httpMethod: Http.Method): Int {

            val baseHeaderContent: ImmutableList<Int> = ImmutableList.from(
                SMS_TUNNEL_PROTOCOL_VERSION.length,
                MAGIC_STRING.length,
                REQUEST_NUMBER_LENGTH,
                httpMethod.name.length,
                FRAGMENT_HEADER_LENGTH
            )

            return baseHeaderContent.fold(0) { acc, i -> acc + i + 1 }
        }

        fun formatSMS(msg: String, httpMethod: Http.Method, currentRequestCounter: Long): MutableList<String> {
            val packets = mutableListOf<String>()

            var packetCount = 1
            var msgIdx = 0
            var currentFragmentSize = 0

            // first compute the number of fragment required for the input message
            val headerSize = computeRequestHeaderLength(httpMethod = httpMethod)

            if(PACKET_SIZE < msg.length + headerSize) {
                val remainderMsgLength = msg.length + headerSize - PACKET_SIZE
                packetCount += kotlin.math.ceil(remainderMsgLength.toDouble() / (PACKET_SIZE - FRAGMENT_HEADER_LENGTH)).toInt()
            }

            if (packetCount > MAX_PACKET_NUMBER) {
                throw IllegalArgumentException("Message size is too long")
            }


            while(msgIdx < msg.length) {

                // first fragment needs special header
                val requestHeader: String = if(msgIdx == 0) {
                    val currentRequestCounterPadded = currentRequestCounter.toString().padStart(REQUEST_NUMBER_LENGTH, '0')
                    val fragmentCountPadded = packetCount.toString().padStart(FRAGMENT_HEADER_LENGTH, '0')
                    "$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-$currentRequestCounterPadded-${httpMethod.name}-$fragmentCountPadded-"
                } else {
                    val fragmentNumber = currentFragmentSize.toString().padStart(FRAGMENT_HEADER_LENGTH, '0')
                    "$fragmentNumber-"
                }

                val remainingSpace = PACKET_SIZE - requestHeader.length
                val currentFragment = requestHeader + msg.substring(msgIdx, min(msgIdx + remainingSpace, msg.length))
                msgIdx = min(msgIdx + remainingSpace, msg.length)

                packets.add(currentFragment)
                currentFragmentSize += 1
            }

            return packets
        }

        fun parseSMS(packets: MutableList<String>): String {
            var packetContent = ""

            for (packet: String in packets) {
                packetContent += packet
            }

            return packetContent
        }

        fun stringToList(jsonString: String): MutableList<String> {
            val list: MutableList<String> = ArrayList()
            return Gson().fromJson(jsonString, list.javaClass)
        }

        fun listToString(list: MutableList<String>): String {
            return Gson().toJson(list).toString()
        }
    }
}
