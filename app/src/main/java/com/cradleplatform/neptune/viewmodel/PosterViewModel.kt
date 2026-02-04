package com.cradleplatform.neptune.viewmodel

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for PosterActivity to handle configuration changes like screen rotation.
 * Preserves zoom level and scroll position across configuration changes.
 */
@HiltViewModel
class PosterViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_ZOOM_STATE = "zoom_state"
    }

    /**
     * Get the saved zoom state bundle
     */
    fun getZoomState(): Bundle? {
        return savedStateHandle.get<Bundle>(KEY_ZOOM_STATE)
    }

    /**
     * Save the zoom state bundle from TouchImageView
     */
    fun saveZoomState(state: Bundle) {
        savedStateHandle[KEY_ZOOM_STATE] = state
    }
}

