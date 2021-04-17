package com.cradleVSA.neptune.manager

import android.app.Activity.RESULT_OK
import android.content.Context
import android.widget.Toast
import com.cradleVSA.neptune.view.SplashActivity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
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
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) &&
                appUpdateInfo.updatePriority() >= 5
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    activity,
                    MY_REQUEST_CODE
                )
            }
        }
    }

    fun handleResult(requestCode: Int, resultCode: Int,) {
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(context, "Update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
