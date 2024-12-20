package com.cradleplatform.neptune.utilities.connectivity.api24

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import javax.inject.Inject

/**
 * An API 24+ implementation of Network Capability Monitoring.
 * A modification from
 * https://medium.com/geekculture/implementing-an-active-network-state-monitor-in-android-dbbc24cf2bc5
 * Note: Callback is not supported under API 24?
 */
class NetworkMonitoringUtil @Inject constructor(
    private val context: Context,
    private val mNetworkStateManager: NetworkStateManager
) : ConnectivityManager.NetworkCallback() {
    private val mNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private val mConnectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // private val mNetworkStateManager: NetworkStateManager = NetworkStateManager.getInstance()

    companion object {
        private const val TAG = "NetworkMonitoringUtil"
    }

    init {
        Log.d(TAG, "NetworkMonitoringUtil initialized")
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        Log.d(TAG, "onAvailable() called: Connected to network")
        // NOTE: DO NOT CALL getNetworkCapabilities() in here. Will cause
        // race condition.
        mNetworkStateManager.setInternetConnectivityStatus(true)
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        Log.e(TAG, "onLost() called: Lost network connection")
        mNetworkStateManager.setInternetConnectivityStatus(false)
        mNetworkStateManager.setWifiConnectivityStatus(false)
        mNetworkStateManager.setCellularDataConnectivityStatus(false)
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
    ) {
        super.onCapabilitiesChanged(network, networkCapabilities)
        Log.d(TAG, "onCapabilityChanged() called.")
        // Check for specific network capabilities and perform actions accordingly
        // Wi-Fi capabilities changed
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            mNetworkStateManager.setWifiConnectivityStatus(true)
        } else {
            mNetworkStateManager.setWifiConnectivityStatus(false)
        }
        // Cellular data capabilities changed
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            mNetworkStateManager.setCellularDataConnectivityStatus(true)
        } else {
            mNetworkStateManager.setCellularDataConnectivityStatus(false)
        }
    }

    /**
     * Registers the Network-Request callback
     * (Note: Register only once to prevent duplicate callbacks)
     */
    fun registerNetworkCallbackEvents() {
        Log.d(TAG, "registerNetworkCallbackEvents() called")
        mConnectivityManager.registerNetworkCallback(mNetworkRequest, this)
    }

    /**
     * Unregisters the Network-Request callback
     */
    fun unregisterNetworkCallbackEvents() {
        Log.d(TAG, "unregisterNetworkCallbackEvents() called")
        mConnectivityManager.unregisterNetworkCallback(this)
    }

    /**
     * Check current Network state
     */
    fun checkNetworkState() {
        try {
            val networkInfo = mConnectivityManager.activeNetworkInfo
            // Set the initial value for the live-data
            mNetworkStateManager.setInternetConnectivityStatus(
                networkInfo != null
                    && networkInfo.isConnected
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }
}
