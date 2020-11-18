package com.cradleVSA.neptune.ext

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Switches context to the main thread in order to set the value instantly.
 */
suspend fun <T> MutableLiveData<T>.setValueOnMainThread(
    newValue: T
) = withContext(Dispatchers.Main) { value = newValue }
