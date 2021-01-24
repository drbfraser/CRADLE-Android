package com.cradleVSA.neptune.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Monitors the network state.
 */
class NetworkWatcher @Inject constructor(
    @ApplicationContext context: Context
) : ConnectivityManager.NetworkCallback() {
    private val connectivityManager: ConnectivityManager = ContextCompat
        .getSystemService(context, ConnectivityManager::class.java)
        ?: error("missing ConnectivityManager")

    private val _isConnectedToInternet = MutableLiveData<Boolean>()
    val isConnectedToInternet: LiveData<Boolean> = _isConnectedToInternet

    private var isListening = false

    fun stopListeningToNetwork() {
        synchronized(this) {
            if (!isListening) {
                return@synchronized
            }
            connectivityManager.unregisterNetworkCallback(this)
            isListening = false
        }
    }

    fun startListeningToNetwork() {
        synchronized(this) {
            if (isListening) {
                return@synchronized
            }
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().build(),
                this
            )
            isListening = true
        }
    }

    override fun onAvailable(network: Network) {
        _isConnectedToInternet.postValue(true)
    }

    override fun onLost(network: Network) {
        _isConnectedToInternet.postValue(false)
    }
}
