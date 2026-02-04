package com.cradleplatform.neptune.viewmodel

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
        private const val KEY_ZOOM_SCALE = "zoom_scale"
        private const val KEY_SCROLL_X = "scroll_x"
        private const val KEY_SCROLL_Y = "scroll_y"
    }

    /**
     * Get the saved zoom scale
     */
    fun getZoomScale(): Float? {
        return savedStateHandle.get<Float>(KEY_ZOOM_SCALE)
    }

    /**
     * Save the current zoom scale
     */
    fun saveZoomScale(scale: Float) {
        savedStateHandle[KEY_ZOOM_SCALE] = scale
    }

    /**
     * Get the saved scroll position X
     */
    fun getScrollX(): Float? {
        return savedStateHandle.get<Float>(KEY_SCROLL_X)
    }

    /**
     * Save the current scroll position X
     */
    fun saveScrollX(x: Float) {
        savedStateHandle[KEY_SCROLL_X] = x
    }

    /**
     * Get the saved scroll position Y
     */
    fun getScrollY(): Float? {
        return savedStateHandle.get<Float>(KEY_SCROLL_Y)
    }

    /**
     * Save the current scroll position Y
     */
    fun saveScrollY(y: Float) {
        savedStateHandle[KEY_SCROLL_Y] = y
    }
}

