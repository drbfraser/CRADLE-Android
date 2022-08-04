package com.cradleplatform.neptune.utilities

import android.util.Base64
import com.google.gson.Gson
import javax.crypto.SecretKey

enum class RelayAction {
    READING,
    REFERRAL,
    FORMRESPONSE
}

class SMSMessageFormatter {
    companion object {
        // send 5 packets at once through MultipartTextMessage
        private const val PACKET_SIZE = 153 * 2
        private const val MAX_PACKET_NUMBER = 99

        fun encodeMsg(msg: String, action: RelayAction, secretKey: SecretKey): String {
            var smsMsg = "$action|$msg"
            val formattedMsg = AESEncrypter.encrypt(GzipCompressor.compress(smsMsg), secretKey)
            return Base64.encodeToString(formattedMsg, Base64.DEFAULT)
        }

        fun formatSMS(msg: String): MutableList<String> {
            val packets = mutableListOf<String>()

            // Start from 0
            val packetCount = kotlin.math.ceil(msg.length.toDouble() / PACKET_SIZE).toInt() - 1

            if (packetCount > MAX_PACKET_NUMBER) {
                throw IllegalArgumentException("Message size is too long")
            }

            // function parameters are immutable
            var smsMsg = msg

            while (smsMsg.isNotEmpty()) {
                var remainingLength = if (smsMsg.length < PACKET_SIZE) smsMsg.length else PACKET_SIZE

                packets.add(smsMsg.substring(0, remainingLength))
                smsMsg = smsMsg.drop(remainingLength)
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
