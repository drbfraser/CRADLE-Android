package com.cradleVSA.neptune.manager

import android.content.Context
import android.widget.Toast
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.view.SplashActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    companion object {
        private const val MAX_PRIORITY = 5
    }

    suspend fun immediateUpdate(activity: SplashActivity): Boolean {
        val appUpdateInfo = appUpdateManager.requestAppUpdateInfo()
        var updateSuccessful = false
        if (isMaxPriorityUpdateAvailable(appUpdateInfo) || isUpdateInProgress(appUpdateInfo)) {
            val task = appUpdateManager.startUpdateFlow(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
            task.addOnCompleteListener {
                if (it.isSuccessful) {
                    updateSuccessful = true
                    Toast.makeText(context, context.getString(R.string.update_successful), Toast.LENGTH_SHORT).show()
                } else {
                    updateSuccessful = false
                    Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
        return updateSuccessful
    }

    private fun isMaxPriorityUpdateAvailable(appUpdateInfo: AppUpdateInfo): Boolean {
        return appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) &&
            appUpdateInfo.updatePriority() >= MAX_PRIORITY
    }

    private fun isUpdateInProgress(appUpdateInfo: AppUpdateInfo): Boolean {
        return appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
    }
}
