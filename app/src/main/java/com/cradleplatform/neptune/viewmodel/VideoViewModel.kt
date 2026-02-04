package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for VideoActivity to handle configuration changes like screen rotation.
 * Preserves video playback position across configuration changes.
 */
@HiltViewModel
class VideoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_VIDEO_POSITION = "video_position"
    }

    /**
     * Get the saved video position in milliseconds
     */
    fun getVideoPosition(): Int {
        return savedStateHandle.get<Int>(KEY_VIDEO_POSITION) ?: 0
    }

    /**
     * Save the current video position in milliseconds
     */
    fun saveVideoPosition(position: Int) {
        savedStateHandle[KEY_VIDEO_POSITION] = position
    }
}

