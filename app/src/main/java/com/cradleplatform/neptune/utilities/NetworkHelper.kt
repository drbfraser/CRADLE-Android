package com.cradleplatform.neptune.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

enum class NetworkStatus {
    WIFI, CELLULAR, ETHERNET, NO_NETWORK
}

class NetworkHelper {
    companion object {
        fun isConnectedToInternet(context: Context): NetworkStatus {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val capabilities =
                    connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

                if (capabilities != null) {
                    when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                            return NetworkStatus.CELLULAR
                        }
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            return NetworkStatus.WIFI
                        }
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                            return NetworkStatus.ETHERNET
                        }
                    }
                } else {
                    // If the sdk version is below 23, we will use a deprecated method
                    val activeNetworkInfo = connectivityManager.activeNetworkInfo
                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                        return NetworkStatus.WIFI
                    }
                }
            }

            return NetworkStatus.NO_NETWORK
        }
    }
}
