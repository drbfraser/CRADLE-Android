package com.cradleVSA.neptune.ext

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

private const val USE_NON_DEPRECATED_CONNECTIVITY_MANAGER_METHODS = false

/**
 * Checks if the device currently has a connection. Note: This simply checks whether the
 * NetworkState is connected ([NetworkCapabilities.NET_CAPABILITY_INTERNET]); it does not check
 * whether the NetworkState is validated ([NetworkCapabilities.NET_CAPABILITY_VALIDATED]).
 */
fun ConnectivityManager.isConnected(): Boolean =
    if (USE_NON_DEPRECATED_CONNECTIVITY_MANAGER_METHODS &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    ) {
        getNetworkCapabilities(activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
    } else {
        // We're just using the same method as NetworkStateTracker in androidx.work at this
        // point.
        // activeNetworkInfo deprecated in API 29 (Q):
        // https://developer.android.com/reference/android/net/ConnectivityManager#getActiveNetworkInfo()
        // isConnected deprecated in API 29 (Q):
        // https://developer.android.com/reference/android/net/NetworkInfo#isConnected()
        // If these get removed, let's see what androidx.work does.
        activeNetworkInfo?.isConnected ?: false
    }
