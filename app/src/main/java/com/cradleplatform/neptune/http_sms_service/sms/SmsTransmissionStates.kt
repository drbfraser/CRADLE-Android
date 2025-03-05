package com.cradleplatform.neptune.http_sms_service.sms

enum class SmsTransmissionStates {
    GETTING_READY_TO_SEND,
    SENDING_TO_RELAY_SERVER,
    WAITING_FOR_SERVER_RESPONSE,
    RECEIVING_SERVER_RESPONSE,
    WAITING_FOR_USER_RESPONSE,
    DONE,
    EXCEPTION,
    RETRANSMISSION,
    TIME_OUT
}
