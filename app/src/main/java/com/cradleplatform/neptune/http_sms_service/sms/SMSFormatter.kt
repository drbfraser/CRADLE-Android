package com.cradleplatform.neptune.http_sms_service.sms

import android.os.Build
import androidx.annotation.RequiresApi
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.GzipCompressor
import com.google.firebase.crashlytics.internal.model.ImmutableList
import java.util.Base64
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
        const val PACKET_SIZE = 153
        // private const val MAX_PACKET_NUMBER = 99

        //Fixed strings, prefixes, suffixes involved in the SMS Protocol
        const val SMS_TUNNEL_PROTOCOL_VERSION = "01"
        private const val SMS_ACK_SUFFIX = "ACK"
        const val MAGIC_STRING = "CRADLE"
        private const val REPLY_SUCCESS = "REPLY"
        private const val REPLY_ERROR = "REPLY_ERROR"
        private const val REPLY_ERROR_ENCRYPTED = "REPLY_ERROR_ENC"
        private const val REPLY_ERROR_CODE_PREFIX = "ERR"

        //Lengths for different parts of the SMS Protocol
        private const val REPLY_ERROR_CODE_LENGTH = 3
        const val FRAGMENT_HEADER_LENGTH = 3
        private const val REQUEST_NUMBER_LENGTH = 6

        //positions of request identifiers inside different messages of the SMS protocol
        private const val POS_FIRST_MSG_REQUEST_COUNTER = 1
        private const val POS_ACK_MSG_REQUEST_COUNTER = 1
        private const val POS_REPLY_SUCCESS_REQUEST_COUNTER = 1
        private const val POS_REPLY_ERROR_REQUEST_COUNTER = 1

        //positions for data inside different messages of the SMS protocol
        private const val POS_FIRST_MSG_DATA = 3
        private const val POS_REST_MSG_DATA = 2
        private const val POS_REPLY_SUCCESS_DATA = 3
        private const val POS_REPLY_ERROR_DATA = 4

        //position of error code in error message
        private const val POS_REPLY_ERROR_CODE = 3

        //positions for total fragments in transaction
        private const val POS_FIRST_NUM_FRAGMENTS = 2
        private const val POS_REPLY_SUCCESS_NUM_FRAGMENTS = 2
        private const val POS_REPLY_ERROR_NUM_FRAGMENTS = 2

        //positions for current fragment number
        private const val POS_ACK_CURR_FRAGMENT = 2
        private const val POS_REST_CURR_FRAGMENT = 1

        val ackRegexPattern =
            Regex(
                "^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-" +
                    "(\\d{$REQUEST_NUMBER_LENGTH})-(\\d{$FRAGMENT_HEADER_LENGTH})-$SMS_ACK_SUFFIX$"
            )

        val firstRegexPattern =
            Regex(
                "^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-" +
                    "(\\d{$REQUEST_NUMBER_LENGTH})-(\\d{$FRAGMENT_HEADER_LENGTH})-(.+$)"
            )

        val restRegexPattern =
            Regex(
                "^(\\d{$FRAGMENT_HEADER_LENGTH})-(.+$)"
            )

        val firstErrorReplyPattern =
            Regex(
                "^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-" +
                    "(\\d{$REQUEST_NUMBER_LENGTH})-$REPLY_ERROR(?:_ENC)?-(\\d{$FRAGMENT_HEADER_LENGTH})-" +
                    "$REPLY_ERROR_CODE_PREFIX(\\d{$REPLY_ERROR_CODE_LENGTH})-(.+$)"
            )

        val firstSuccessReplyPattern =
            Regex(
                "^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-" +
                    "(\\d{$REQUEST_NUMBER_LENGTH})-$REPLY_SUCCESS-" +
                    "(\\d{$FRAGMENT_HEADER_LENGTH})-(.+$)"
            )

        val encryptedErrorReplyPattern =
            Regex(
                "^$SMS_TUNNEL_PROTOCOL_VERSION-$MAGIC_STRING-" +
                        "(\\d{$REQUEST_NUMBER_LENGTH})-$REPLY_ERROR_ENCRYPTED-(\\d{$FRAGMENT_HEADER_LENGTH})-" +
                        "$REPLY_ERROR_CODE_PREFIX(\\d{$REPLY_ERROR_CODE_LENGTH})-(.+$)"
            )

        // TODO: CHANGE TEST
        @RequiresApi(Build.VERSION_CODES.O)
        fun encodeMsg(msg: String, secretKey: String): String {
            if (secretKey.isNotEmpty()) {
                val encryptedMsg =
                    AESEncryptor.encryptString(GzipCompressor.compress(msg), secretKey)
                return Base64.getEncoder().encodeToString(encryptedMsg.toByteArray()) }
            return ""
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun decodeMsg(msg: String, secretKey: String): String {
            if (secretKey.isNotEmpty()) {
                val decodedBytes = Base64.getDecoder().decode(msg)
                val decryptedMsg = AESEncryptor.decryptString(String(decodedBytes), secretKey)
                return GzipCompressor.decompress(decryptedMsg)
            }
            return "ERROR: key is empty"
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
    }

    fun getRequestIdentifier(smsMessage: String): String {
        return if (isFirstReplyError(smsMessage))
            firstErrorReplyPattern.find(smsMessage)?.
                groupValues!![POS_REPLY_ERROR_REQUEST_COUNTER]
        else
            firstSuccessReplyPattern.find(smsMessage)?.
                groupValues!![POS_REPLY_SUCCESS_REQUEST_COUNTER]
    }

    fun getTotalNumMessages(smsMessage: String): Int {
        return if (isFirstReplyError(smsMessage))
            firstErrorReplyPattern.find(smsMessage)?.
                groupValues!![POS_REPLY_ERROR_NUM_FRAGMENTS].toInt()
        else
            firstSuccessReplyPattern.find(smsMessage)?.
                groupValues!![POS_REPLY_SUCCESS_NUM_FRAGMENTS].toInt()
    }

    fun getFirstMessageString(smsMessage: String): String {
        return if (isFirstReplyError(smsMessage))
            firstErrorReplyPattern.find(smsMessage)?.groupValues!![POS_REPLY_ERROR_DATA]
        else
            firstSuccessReplyPattern.find(smsMessage)?.groupValues!![POS_REPLY_SUCCESS_DATA]
    }

    fun getMessageNumber(smsMessage: String): Int {
        return restRegexPattern.find(smsMessage)?.groupValues!![POS_REST_CURR_FRAGMENT].toInt()
    }

    fun getRestMessageString(smsMessage: String): String {
        return restRegexPattern.find(smsMessage)?.groupValues!![POS_REST_MSG_DATA]
    }

    fun isAckMessage(message: String): Boolean {
        return ackRegexPattern.matches(message)
    }

    fun isRestMessage(message: String): Boolean {
        return restRegexPattern.matches(message)
    }

    fun isFirstReplyMessage(message: String): Boolean {
        return firstSuccessReplyPattern.matches(message) || firstErrorReplyPattern.matches(message)
    }

    fun isFirstReplyError(message: String): Boolean {
        return firstErrorReplyPattern.matches(message)
    }

    fun getErrorCode(message: String): Int {
        return firstErrorReplyPattern.find(message)?.groupValues!![POS_REPLY_ERROR_CODE].toInt()
    }

    fun isReplyErrorEncrypted(message: String): Boolean {
        return encryptedErrorReplyPattern.matches(message)
    }
}
