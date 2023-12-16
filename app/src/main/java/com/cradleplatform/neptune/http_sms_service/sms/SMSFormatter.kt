package com.cradleplatform.neptune.utilities

import android.util.Log
import com.google.firebase.crashlytics.internal.model.ImmutableList
import com.google.gson.Gson
import org.json.JSONObject
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
        // private const val MAX_PACKET_NUMBER = 99

        // Http Header
        const val SMS_TUNNEL_PROTOCOL_VERSION = "01"
        const val MAGIC_STRING = "CRADLE"
        const val FRAGMENT_HEADER_LENGTH = 3
        private const val REQUEST_NUMBER_LENGTH = 6

        val ackRegexPattern = Regex("^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-(\\d{6})-(\\d{3})-ACK$")
        val firstRegexPattern = Regex("^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-(\\d{6})-(\\d{3})-(.+)$")
        val restRegexPattern = Regex("^(\\d{3})-(.+)$")

        // TODO: CHANGE TEST
        fun encodeMsg(msg: String, secretKey: String): String {

            val jsonObject = JSONObject(secretKey)
            val key = jsonObject.optString("sms_key")

            if (key.isNotEmpty()) {
                return AESEncryptor.encryptString(
                    GzipCompressor.compress(msg),
                    key
                )
            }
            return ""
        }

        fun decodeMsg(msg: String, secretKey: String): String {
            // Extracts actual Key from secretKey, which is a JSON String
            val key = JSONObject(secretKey).optString("sms_key")
            if (key.isNotEmpty()) {
                GzipCompressor.decompress(
                    AESEncryptor.decryptString(msg, key)
                ).let{
                    Log.d("SMS_Formatter", it)
                    return it
                }
            } else {
                return "ERROR: key is empty"
            }
        }

        private fun computeRequestHeaderLength(): Int {

            val baseHeaderContent: ImmutableList<Int> = ImmutableList.from(
                SMS_TUNNEL_PROTOCOL_VERSION.length,
                MAGIC_STRING.length,
                REQUEST_NUMBER_LENGTH,
                FRAGMENT_HEADER_LENGTH
            )

            return baseHeaderContent.fold(0) { acc, i -> acc + i + 1 }
        }

        fun formatSMS(
            msg: String,
            currentRequestCounter: Long
        ): MutableList<String> {
            val packets = mutableListOf<String>()

            var packetCount = 1
            var msgIdx = 0
            var currentFragmentSize = 0

            // first compute the number of fragment required for the input message
            val headerSize = computeRequestHeaderLength()

            if (PACKET_SIZE < msg.length + headerSize) {
                val remainderMsgLength = msg.length + headerSize - PACKET_SIZE
                packetCount += kotlin.math.ceil(
                    remainderMsgLength.toDouble() / (PACKET_SIZE - FRAGMENT_HEADER_LENGTH)
                ).toInt()
            }

            // if (packetCount > MAX_PACKET_NUMBER) {
            //     throw IllegalArgumentException("Message size is too long")
            // }

            while (msgIdx < msg.length) {
                // first fragment needs special header
                val requestHeader: String = if (msgIdx == 0) {
                    val currentRequestCounterPadded =
                        currentRequestCounter.toString().padStart(REQUEST_NUMBER_LENGTH, '0')
                    val fragmentCountPadded =
                        packetCount.toString().padStart(FRAGMENT_HEADER_LENGTH, '0')
                    """
                    $SMS_TUNNEL_PROTOCOL_VERSION-
                    $MAGIC_STRING-
                    $currentRequestCounterPadded-
                    $fragmentCountPadded-
                    """.trimIndent().replace("\n", "")
                } else {
                    val fragmentNumber =
                        currentFragmentSize.toString().padStart(FRAGMENT_HEADER_LENGTH, '0')
                    "$fragmentNumber-"
                }
                val remainingSpace = PACKET_SIZE - requestHeader.length
                val currentFragment =
                    requestHeader + msg.substring(msgIdx, min(msgIdx + remainingSpace, msg.length))
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

    fun getRequestIdentifier(smsMessage: String): String {
        return firstRegexPattern.find(smsMessage)?.groupValues!![1]
    }
    fun getTotalNumMessages(smsMessage: String): Int {
        return firstRegexPattern.find(smsMessage)?.groupValues!![2].toInt()
    }

    fun getFirstMessageString(smsMessage: String): String {
        return firstRegexPattern.find(smsMessage)?.groupValues!![3]
    }

    fun getMessageNumber(smsMessage: String): Int {
        return restRegexPattern.find(smsMessage)?.groupValues!![1].toInt()
    }

    fun getRestMessageString(smsMessage: String): String {
        return restRegexPattern.find(smsMessage)?.groupValues!![2]
    }

    fun isFirstMessage(message: String): Boolean {
        return firstRegexPattern.matches(message)
    }

    fun isAckMessage(message: String): Boolean {
        return ackRegexPattern.matches(message)
    }

    fun isRestMessage(message: String): Boolean {
        return restRegexPattern.matches(message)
    }
}
