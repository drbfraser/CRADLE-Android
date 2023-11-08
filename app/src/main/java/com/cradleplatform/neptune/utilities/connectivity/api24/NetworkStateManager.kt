package com.cradleplatform.neptune.utilities.connectivity.api24

import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton Manager class to maintain current Network-Status throughout the application.
 * Can provide Wifi, Cellular, and general Internet connectivity status
 * Modified from:
 * https://medium.com/geekculture/implementing-an-active-network-state-monitor-in-android-dbbc24cf2bc5
 */

// Note: We want WIFI > CELLULAR_DATA, SMS, NONE
enum class ConnectivityOptions {
    NONE,
    SMS,
    CELLULAR_DATA,
    WIFI,
}
@Singleton
class NetworkStateManager @Inject constructor() {
    // Defaulting to False
    private val activeWifiStatus = MutableLiveData<Boolean>()
    private val activeCellularDataStatus = MutableLiveData<Boolean>()
    private val activeInternetStatus = MutableLiveData<Boolean>()
    // private val activeSmsStatus = MutableLiveData<Boolean>() // TODO: Implement another monitor
    companion object {
        private const val TAG = "NetworkStateManager"
        private var INSTANCE: NetworkStateManager? = null
        /** The defaultNetworkCallback is only available on >= API 24 */
        private const val DEFAULT_NETWORK_CALLBACK_SUPPORTED_API_LEVEL = 24
        private val isCallbackSupported =
            Build.VERSION.SDK_INT >= DEFAULT_NETWORK_CALLBACK_SUPPORTED_API_LEVEL
        @Synchronized
        fun getInstance(): NetworkStateManager {
            if (INSTANCE == null) {
                Log.d(TAG, "getInstance() called: Creating new instance")
                INSTANCE = NetworkStateManager()
            }
            return INSTANCE!!
        }
    }

    fun getConnectivity(): ConnectivityOptions {
        if (activeInternetStatus.value == true) {
            if (activeWifiStatus.value == true) {
                return ConnectivityOptions.WIFI
            } else if (activeCellularDataStatus.value == true) {
                return ConnectivityOptions.CELLULAR_DATA
            }
            // TODO: To be finished after implementing TelephonyManager in NetworkMonitoringUtil or
            //  another class
            // } else if (activeSmsStatus.value == true) {
            //         return ConnectivityOptions.SMS
        }
        return ConnectivityOptions.NONE
    }

    /**
     * Returns if Wifi OR Cellular data connectivity are available
     */
    fun getInternetConnectivityStatus(): LiveData<Boolean> {
        Log.d(TAG, "getInternetConnectivityStatus() called")
        return activeInternetStatus
    }

    /**
     * Updates the active internet status live-data
     */
    fun setInternetConnectivityStatus(connectivityStatus: Boolean) {
        Log.d(
            TAG,
            "setInternetConnectivityStatus() called with: " +
                "connectivityStatus = [$connectivityStatus]"
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activeInternetStatus.value = connectivityStatus
        } else {
            activeInternetStatus.postValue(connectivityStatus)
        }
    }

    /**
     * Updates the active wifi status live-data
     */
    fun setWifiConnectivityStatus(connectivityStatus: Boolean) {
        Log.d(
            TAG,
            "setWifiConnectivityStatus() called with: " +
                "connectivityStatus = [$connectivityStatus]"
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activeWifiStatus.value = connectivityStatus
        } else {
            activeWifiStatus.postValue(connectivityStatus)
        }
    }

    /**
     * Returns the current wifi connectivity status
     */
    fun getWifiConnectivityStatus(): LiveData<Boolean> {
        Log.d(TAG, "getWifiConnectivityStatus() called")
        return activeWifiStatus
    }

    /**
     * Updates the active cellular data status live-data
     */
    fun setCellularDataConnectivityStatus(connectivityStatus: Boolean) {
        Log.d(
            TAG,
            "setCellularDataConnectivityStatus() called with: " +
                "connectivityStatus = [$connectivityStatus]"
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            activeCellularDataStatus.value = connectivityStatus
        } else {
            activeCellularDataStatus.postValue(connectivityStatus)
        }
    }

    /**
     * Returns the current cellular data connectivity status
     * NOTE: If WIFI is ON, this will return FALSE
     */
    fun getCellularDataConnectivityStatus(): LiveData<Boolean> {
        Log.d(TAG, "getCellularDataConnectivityStatus() called")
        return activeCellularDataStatus
    }

    // /**
    //  * Updates the active sms capability status live-data
    //  */
    // internal fun setSmsConnectivityStatus(connectivityStatus: Boolean) {
    //     Log.d(
    //         TAG, "setSmsConnectivityStatus() called with: " +
    //             "connectivityStatus = [$connectivityStatus]")
    //     if (Looper.myLooper() == Looper.getMainLooper()) {
    //         activeSmsStatus.value = connectivityStatus
    //     } else {
    //         activeSmsStatus.postValue(connectivityStatus)
    //     }
    // }
    //
    // /**
    //  * Returns the current active sms capability status
    //  */
    // fun getSmsConnectivityStatus(): LiveData<Boolean> {
    //     Log.d(TAG, "getSmsConnectivityStatus() called")
    //     return activeSmsStatus
    // }
}
