package com.cradleplatform.neptune.utilities.connectivity.api24

import android.content.Context
import com.cradleplatform.neptune.utilities.CustomToast

/**
 * A function that displays a Toast message or run a custom function if none of the connectivity
 * options are available.
 * @param context such as "this"
 * @param networkStateManager pass in a reference
 * @param redirectFunction Pass a function such as starting an Intent to
 * SyncActivity when no other options
 * */
fun displayConnectivityToast(
    context: Context,
    networkStateManager: NetworkStateManager,
    redirectFunction: () -> Unit
) {
    when (networkStateManager.getConnectivity()) {
        ConnectivityOptions.WIFI -> {
            CustomToast.longToast(
                context,
                "You are connected to Wifi network."
            )
        }
        ConnectivityOptions.CELLULAR_DATA -> {
            CustomToast.longToast(
                context,
                "You are connected to CELLULAR network, charges may apply."
            )
        }
        // TODO: update after finishing implementing SMS sync
        // ConnectivityOptions.SMS -> {
        //     CustomToast.shortToast(
        //         context,
        //         "Data will be transmitted over SMS, charges may apply"
        //     )
        // }
        // ConnectivityOptions.NONE -> {
        //     redirectFunction()
        // }
        else -> {
            CustomToast.shortToast(
                context,
                "Make sure you are connected to Internet"
            )
            redirectFunction()
        }
    }
}
