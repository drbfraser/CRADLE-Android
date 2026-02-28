package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for PinPassActivity to handle configuration changes like screen rotation.
 * Preserves PIN entry state across configuration changes.
 */
@HiltViewModel
class PinPassViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_CONFIRM_PIN = "confirm_pin"
        private const val KEY_TEMP_PIN = "temp_pin"
        private const val KEY_HEADER_TEXT = "header_text"
    }

    /**
     * Whether we are in the "confirm PIN" stage (change PIN flow).
     */
    var isConfirmingPin: Boolean
        get() = savedStateHandle.get<Boolean>(KEY_CONFIRM_PIN) ?: false
        set(value) { savedStateHandle[KEY_CONFIRM_PIN] = value }

    /**
     * The temporary PIN entered in the first step of the change PIN flow.
     */
    var tempPin: String
        get() = savedStateHandle.get<String>(KEY_TEMP_PIN) ?: ""
        set(value) { savedStateHandle[KEY_TEMP_PIN] = value }

    /**
     * The header text to display (so we can restore it after rotation).
     */
    var headerText: String
        get() = savedStateHandle.get<String>(KEY_HEADER_TEXT) ?: ""
        set(value) { savedStateHandle[KEY_HEADER_TEXT] = value }
}

