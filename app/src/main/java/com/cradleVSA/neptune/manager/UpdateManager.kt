package com.cradleVSA.neptune.manager

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.net.RestApi
import com.cradleVSA.neptune.view.SplashActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val appUpdateManager = AppUpdateManagerFactory.create(context)
    private val appUpdateInfoTask = appUpdateManager.appUpdateInfo
    private val MY_REQUEST_CODE = 1

    fun immediateUpdate(activity: SplashActivity) {
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                && appUpdateInfo.updatePriority() >= 5
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    // Pass the intent that is returned by 'getAppUpdateInfo()'.
                    appUpdateInfo,
                    // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                    AppUpdateType.IMMEDIATE,
                    // The current activity making the update request.
                    activity,
                    // Include a request code to later monitor this update request.
                    MY_REQUEST_CODE)

            }
        }
    }

    fun handleResult(requestCode: Int, resultCode: Int,) {
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
                // If the update is cancelled or fails,
                // you can request to start the update again.
            }
        }
    }
}

