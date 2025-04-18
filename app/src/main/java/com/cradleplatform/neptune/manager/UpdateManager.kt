package com.cradleplatform.neptune.manager

import android.app.Activity.RESULT_OK
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.introduction.SplashActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(context)

    companion object {
        private const val TAG = "UpdateManager"
        private const val MAX_PRIORITY = 5
    }

    suspend fun immediateUpdate(activity: SplashActivity): Boolean {
        val appUpdateInfo = try {
            appUpdateManager.requestAppUpdateInfo()
        } catch (e: InstallException) {
            Log.e(TAG, "Error while checking for updates, error code ${e.errorCode}", e)
            return false
        }

        var updateSuccessful = false
        if (isMaxPriorityUpdateAvailable(appUpdateInfo) || isUpdateInProgress(appUpdateInfo)) {
            val task = appUpdateManager.startUpdateFlow(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )

            updateSuccessful = suspendCoroutine<Boolean> { cont ->
                task.addOnCompleteListener {
                    if (it.result == RESULT_OK) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_successful),
                            Toast.LENGTH_SHORT
                        ).show()
                        cont.resume(true)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.update_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                        cont.resume(false)
                    }
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
