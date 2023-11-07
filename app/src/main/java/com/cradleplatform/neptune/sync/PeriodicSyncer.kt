package com.cradleplatform.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for periodically syncing with server.
 * TODO: Add unit test for PeriodicSyncer.
 */
@Singleton
class PeriodicSyncer @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) {
    /**
     * Sets up the periodic work request using WorkManager.
     * Used in two cases:
     * - In CradleApplication.kt, when app starts and user is already logged in
     * - In LoginManager.kt, when user logs in
     */
    fun startPeriodicSync() {
        // For testing: Android's minimum allowable interval
        // For production: user picks time interval (default 24 hours)
        val hours = context.resources.getInteger(R.integer.settings_periodic_sync_hours)

        val workRequest = PeriodicWorkRequestBuilder<SyncAllWorker>(
            hours.toLong(), TimeUnit.HOURS)
            .addTag(PERIODIC_WORK_TAG)
            .build()

        sharedPreferences.edit {
            putString(LAST_SYNC_JOB_UUID, workRequest.id.toString())
        }

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)
        Log.d(TAG, "Unique periodic work ${workRequest.id} enqueued")
    }

    /**
     * Sets up the periodic work request using WorkManager.
     * Used in two cases:
     * - In _, when app is killed (TODO)
     * - In LoginManager.kt, when user logs out
     */
    fun endPeriodicSync() {
        WorkManager.getInstance(context)
                .cancelAllWorkByTag(PERIODIC_WORK_TAG)
        Log.d(TAG, "Unique periodic work cancelled")
    }

    companion object {
        private const val TAG = "PeriodicSync"
        private const val PERIODIC_WORK_TAG =
            "Sync-PeriodicPatientsReadingsAssessmentsReferralsFacilitiesForms"
        private const val PERIODIC_WORK_NAME = "PeriodicSync"
        private const val LAST_SYNC_JOB_UUID = "lastSyncJobUuid"
    }
}