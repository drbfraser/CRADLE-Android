package com.cradleplatform.neptune.utilities

import android.util.Base64
import java.util.TreeMap
import javax.crypto.SecretKey

enum class RelayAction {
    READING,
    REFERRAL,
    FORMRESPONSE
}

class SMSMessageFormatter {
    companion object {
        private const val PACKET_SIZE = 154
        private const val MAX_PACKET_NUMBER = 99

        fun encodeMsg(msg: String, action: RelayAction, secretKey: SecretKey): String {
            var smsMsg = "$action|$msg"
            val formattedMsg = AESEncrypter.encrypt(GzipCompressor.compress(smsMsg), secretKey)
            return Base64.encodeToString(formattedMsg, Base64.DEFAULT)
        }

        fun formatSMS(msg: String): MutableList<String> {
            val packets = mutableListOf<String>()

            var count = 0
            // Start from 0
            val packetCount = kotlin.math.ceil(msg.length.toDouble() / PACKET_SIZE).toInt() - 1

            if (packetCount > MAX_PACKET_NUMBER) {
                throw IllegalArgumentException("Message size is too long")
            }

            // function parameters are immutable
            var smsMsg = msg

            while (smsMsg.isNotEmpty()) {
                var header = "$count"
                if (count == 0) {
                    header = "$packetCount:$header"
                }
                header += "|"

                var remainingLength = if (smsMsg.length < PACKET_SIZE) smsMsg.length else PACKET_SIZE

                packets.add(header + smsMsg.substring(0, remainingLength))
                smsMsg = smsMsg.drop(remainingLength)

                count++
            }

            return packets
        }

        fun parseSMS(packets: MutableList<String>): String {

            val regexPattern = Regex("^([\\d]+)?[\\:]?([\\d]+)+\\|", RegexOption.IGNORE_CASE)
            val packetMap = mutableMapOf<Int, String>()
            var packetSize = 0

            for (packet: String in packets) {
                if (regexPattern.containsMatchIn(packet)) {
                    val matches = regexPattern.find(packet)!!
                    val (packetCount, sequence) = matches.destructured
                    if (sequence.isEmpty()) {
                        throw IllegalArgumentException("Sequence number cannot be empty on a packet")
                    }

                    if (packetCount.isNotEmpty()) {
                        // counts from 0
                        packetSize = packetCount.toInt() + 1
                    }

                    packetMap[sequence.toInt()] = packet.substringAfter('|')

                    if (packetMap.size == packetSize) {
                        break
                    }
                } else {
                    throw IllegalArgumentException("Invalid packet content")
                }
            }

            val sortedPacketMap = TreeMap(packetMap)
            var packetContent = ""

            for ((index, msg) in sortedPacketMap) {
                packetContent += msg
            }

            return packetContent
        }
    }
}
