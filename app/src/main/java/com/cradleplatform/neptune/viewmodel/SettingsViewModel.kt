package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for SettingsFragment to handle configuration changes like screen rotation.
 * Preserves UI state such as selected relay phone number position across configuration changes.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_POSITION = "selected_position"
    }

    /**
     * Get the saved selected position for relay phone number
     */
    fun getSelectedPosition(): Int {
        return savedStateHandle.get<Int>(KEY_SELECTED_POSITION) ?: -1
    }

    /**
     * Save the selected position for relay phone number
     */
    fun saveSelectedPosition(position: Int) {
        savedStateHandle[KEY_SELECTED_POSITION] = position
    }
}

