package com.cradleplatform.neptune.sync.views.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.sync.workers.SyncAllWorker
import com.cradleplatform.neptune.sync.views.SyncActivity
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [SyncActivity]
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val patientDao: PatientDao,
    private val readingDao: ReadingDao,
    private val referralDao: ReferralDao,
    private val assessmentDao: AssessmentDao,
    private val sharedPreferences: SharedPreferences,
    private val workManager: WorkManager,
    private val networkStateManager: NetworkStateManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * The [UUID] for the current unique syncing job.
     */
    private val currentWorkUuid = MutableLiveData<UUID?>(
        try {
            UUID.fromString(sharedPreferences.getString(LAST_SYNC_JOB_UUID, ""))
        } catch (e: IllegalArgumentException) {
            null
        }
    )

    val syncStatus: LiveData<WorkInfo?>
        get() = Transformations.switchMap(currentWorkUuid) { uuid ->
            uuid?.let { workManager.getWorkInfoByIdLiveData(it) }
        }

    private val _patientsAndReadingsToUploadText = MediatorLiveData<String>().apply {
        addSource(syncStatus) {
            if (it?.state?.isFinished == true) {
                // Launch in a coroutine to move DB reads off the main thread (well, even if we use
                // Dispatchers.Main, it will use Room's coroutine's context anyway.
                viewModelScope.launch(Dispatchers.Default) {
                    updatePatientAndReadingsToUploadString()
                }
            } else {
                value = ""
            }
        }
    }
    val patientAndReadingsToUploadText: LiveData<String> = _patientsAndReadingsToUploadText

    private suspend fun updatePatientAndReadingsToUploadString() {
        val numberOfPatientsToUpload = patientDao.countPatientsToUpload()
        val numberOfReadingsToUpload = readingDao.getNumberOfUnUploadedReadings()
        val numberOfReferralsToUpload = referralDao.countReferralsToUpload()
        val numberOfAssessmentsToUpload = assessmentDao.countAssessmentsToUpload()

        Log.d(
            TAG,
            "There are $numberOfPatientsToUpload patients and " +
                "$numberOfReadingsToUpload readings to upload"
        )

        val numberToUploadString = when {
            numberOfPatientsToUpload != 1 || numberOfReadingsToUpload != 1
                || numberOfReferralsToUpload != 1 || numberOfAssessmentsToUpload != 1 -> {
                context.getString(
                    R.string.sync_activity_d_data_to_upload,
                    numberOfPatientsToUpload,
                    numberOfReadingsToUpload,
                    numberOfReferralsToUpload,
                    numberOfAssessmentsToUpload
                )
            }
            else -> {
                context.getString(R.string.sync_activity_nothing_to_upload)
            }
        }

        _patientsAndReadingsToUploadText.setValueOnMainThread(numberToUploadString)
    }

    val isCurrentlySyncing: LiveData<Boolean> = Transformations.map(syncStatus) {
        if (it == null) {
            false
        } else {
            !it.state.isFinished
        }
    }

    val isConnectedToInternet : LiveData<Boolean> =
        networkStateManager.getInternetConnectivityStatus()

    @MainThread
    fun startSyncing() {
        val workRequest = OneTimeWorkRequestBuilder<SyncAllWorker>()
            .addTag(WORK_TAG)
            .build()
        sharedPreferences.edit {
            putString(LAST_SYNC_JOB_UUID, workRequest.id.toString())
        }
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, workRequest)
        currentWorkUuid.value = workRequest.id
        Log.d(TAG, "Work ${workRequest.id} enqueued")
    }

    companion object {
        private const val TAG = "SyncViewModel"
        private const val WORK_TAG =
            "Sync-DownloadPatientsReadingsAssessmentsReferralsFacilitiesForms"
        private const val WORK_NAME = "SyncWorkerUniqueSync"
        private const val LAST_SYNC_JOB_UUID = "lastSyncJobUuid"
    }
}
