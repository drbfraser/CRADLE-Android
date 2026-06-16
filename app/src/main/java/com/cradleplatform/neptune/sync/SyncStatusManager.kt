package com.cradleplatform.neptune.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.utilities.DateUtil
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncState { SYNCING, OFFLINE, FAILED, UNSYNCED_CHANGES, UP_TO_DATE }

data class SyncStatus(
    val state: SyncState,
    val lastFailedSyncDate: String
)

@Singleton
class SyncStatusManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
    networkStateManager: NetworkStateManager,
    private val patientDao: PatientDao,
    private val readingDao: ReadingDao,
    private val referralDao: ReferralDao,
    private val assessmentDao: AssessmentDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val workInfos: LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosByTagLiveData(SyncAllWorker::class.java.name)

    private val isOnline: LiveData<Boolean> = networkStateManager.getInternetConnectivityStatus()

    private val _status = MediatorLiveData<SyncStatus>().apply {
        addSource(workInfos) { recompute() }
        addSource(isOnline) { recompute() }
    }
    val status: LiveData<SyncStatus> = _status.distinctUntilChanged()

    fun refresh() = recompute()

    private fun recompute() {
        scope.launch {
            val isSyncing = workInfos.value?.any { it.state == WorkInfo.State.RUNNING } == true
            val online = isOnline.value != false
            val unsyncedCount = patientDao.countPatientsToUpload() +
                readingDao.getNumberOfUnUploadedReadings() +
                referralDao.countReferralsToUpload() +
                assessmentDao.countAssessmentsToUpload()
            val lastSucceeded = sharedPreferences.getBoolean(SyncAllWorker.LAST_SYNC_SUCCEEDED, true)

            val state = when {
                isSyncing -> SyncState.SYNCING
                !online -> SyncState.OFFLINE
                !lastSucceeded && unsyncedCount > 0 -> SyncState.FAILED
                unsyncedCount > 0 -> SyncState.UNSYNCED_CHANGES
                else -> SyncState.UP_TO_DATE
            }
            _status.postValue(SyncStatus(state, lastFailedSyncDate()))
        }
    }

    private fun lastFailedSyncDate(): String =
        formatDate(sharedPreferences.getString(SyncAllWorker.LAST_FAILED_SYNC_TIME, null))

    private fun formatDate(time: String?): String =
        if (time.isNullOrBlank() || time == SyncAllWorker.LAST_SYNC_DEFAULT) {
            context.getString(R.string.sync_activity_date_never)
        } else {
            DateUtil.getConciseDateString(BigInteger(time), false)
        }
}
