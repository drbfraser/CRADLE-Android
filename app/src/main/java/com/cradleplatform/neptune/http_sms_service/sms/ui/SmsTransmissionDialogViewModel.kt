package com.cradleplatform.neptune.http_sms_service.sms.ui

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


/**
 *
 *
 *
 *
 */
@HiltViewModel
class SmsTransmissionDialogViewModel: ViewModel() {
    val state = MutableLiveData<SmsTransmissionStates>(SmsTransmissionStates.INITIALIZING)
    val progress = MutableLiveData<Float>(0.0F) // 0 - 100
    val displayMessage = MediatorLiveData<String>("Starting...")
    private val totalMessageCount = MutableLiveData<Int>(0)
    private val count = MutableLiveData<Int>(0)

    init {
        // Set up MediatorLiveData dependencies
        displayMessage.addSource(state) {
            updateDisplayMessage(it)
        }
    }

    /**
     * Function to verify that this ViewModel has been re-instantiated for a new transmission.
     */
    fun isNewInstance() = (count.value == 0)

    /**
     * Get number of messages sent or received.
     */
    fun getCount() = count.value!!

    /**
     * Increment number of messages sent or received. Call this after each ACK or after sending an
     * ACK after receiving.
     */
    fun incrementCount() { count.value = count.value!! + 1 }

    /**
     * Reset number of messages sent or received. Call this when changing from SENDING to RECEIVING.
     */
    fun resetCount() { count.value = 0 }

    /**
     * Get total number of messages for sending or receiving.
     */
    fun getTotalMessageCount() = totalMessageCount.value!!

    /**
     * Set total number of message for sending or receiving. Need to set this at the beginning of
     * SENDING and RECEIVING.
     * @param count Total number of expected message to be sent or received.
     */
    fun setTotalMessageCount(count: Int) { totalMessageCount.value = count }

    /**
     * Updates LiveData displayMessage String in the ViewModel for UI components to subscribe to.
     * @param state Use SmsTransmissionStates Enum
     */
    private fun updateDisplayMessage(state: SmsTransmissionStates){
        when (state) {
            SmsTransmissionStates.INITIALIZING -> {
                displayMessage.value = "Getting ready to send..."
            }
            SmsTransmissionStates.SENDING_TO_RELAY_SERVER -> {
                // TODO: format
                displayMessage.value = "Sending x/Y messages to "
            }
            SmsTransmissionStates.WAITING_FOR_SERVER_RESPONSE -> {

            }
            SmsTransmissionStates.RECEIVING_SERVER_RESPONSE -> {

            }
            SmsTransmissionStates.DONE -> {

            }
        }
    }
}