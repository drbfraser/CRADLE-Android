package com.cradleVSA.neptune.utilitiles.livedata

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData

/**
 * A [LiveData] that emits whether the device is connected to the internet.
 * Note: This simply checks whether the NetworkState is connected
 * ([NetworkCapabilities.NET_CAPABILITY_INTERNET]); it does not check whether the NetworkState is
 * validated ([NetworkCapabilities.NET_CAPABILITY_VALIDATED]).
 *
 * Based on androidx.work's NetworkStateTracker
 * (https://github.com/androidx/androidx/blob/androidx-main/work/workmanager/src/main/java/androidx/
 * work/impl/constraints/trackers/NetworkStateTracker.java)
 * and NetworkConnectedController
 * (https://github.com/androidx/androidx/blob/8cb282ccdbb00687dbf253a4419ded0dfc786fb5/work/
 * workmanager/src/main/java/androidx/work/impl/constraints/controllers/
 * NetworkConnectedController.java)
 */
class NetworkAvailableLiveData(context: Context) : LiveData<Boolean>() {
    private val appContext = context.applicationContext

    private val connectivityManager: ConnectivityManager? =
        ContextCompat.getSystemService(context, ConnectivityManager::class.java)
    private val connectivityBroadcastReceiver: BroadcastReceiver?
    private val defaultNetworkCallback: ConnectivityManager.NetworkCallback?
    init {
        if (connectivityManager == null) {
            Log.wtf(TAG, "device is missing ConnectivityManager; defaulting to true")
            // If the device doesn't support ConnectivityManager for whatever reason, assume that
            // we have internet so we don't block.
            postValue(true)
            connectivityBroadcastReceiver = null
            defaultNetworkCallback = null
        } else {
            if (isCallbackSupported) {
                connectivityBroadcastReceiver = null
                defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        postValue(isConnected())
                    }

                    override fun onLost(network: Network) {
                        postValue(isConnected())
                    }
                }
            } else {
                defaultNetworkCallback = null
                connectivityBroadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == ConnectivityManager.CONNECTIVITY_ACTION) {
                            postValue(isConnected())
                        }
                    }
                }
            }
        }
    }

    override fun onActive() {
        connectivityManager ?: return
        if (isCallbackSupported) {
            defaultNetworkCallback ?: return
            connectivityManager.apply {
                Log.d(TAG, "registering ConnectivityManager defaultNetworkCallback")
                registerDefaultNetworkCallback(defaultNetworkCallback)
                value = isConnected()
            }
        } else {
            Log.d(TAG, "registering BroadcastReceiver")
            appContext.registerReceiver(
                connectivityBroadcastReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            )
        }
    }

    override fun onInactive() {
        connectivityManager ?: return
        if (isCallbackSupported) {
            defaultNetworkCallback ?: return
            Log.d(TAG, "unregistering ConnectivityManager defaultNetworkCallback")
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        } else {
            Log.d(TAG, "unregistering BroadcastReceiver")
            appContext.unregisterReceiver(connectivityBroadcastReceiver)
        }
    }

    private fun isConnected(): Boolean = connectivityManager?.run {
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
    } ?: false

    companion object {
        private const val TAG = "NetworkAvailableLiveDat"
        /** The defaultNetworkCallback is only available on >= API 24 */
        private const val DEFAULT_NETWORK_CALLBACK_SUPPORTED_API_LEVEL = 24
        private const val USE_NON_DEPRECATED_CONNECTIVITY_MANAGER_METHODS = false
        private val isCallbackSupported =
            Build.VERSION.SDK_INT >= DEFAULT_NETWORK_CALLBACK_SUPPORTED_API_LEVEL
    }
}
