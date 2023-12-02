package com.cradleplatform.neptune.http_sms_service.sms.ui

enum class SmsTransmissionStates {
    INITIALIZING,
    SENDING_TO_RELAY_SERVER,
    WAITING_FOR_SERVER_RESPONSE,
    RECEIVING_SERVER_RESPONSE,
    DONE
}