package com.cradleplatform.neptune.viewmodel

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
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.ext.setValueOnMainThread
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.view.SyncActivity
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
    private val sharedPreferences: SharedPreferences,
    private val workManager: WorkManager,
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
        val numberOfPatientsToUpload = patientDao.getNumberOfPatientsForUpload()
        val numberOfReadingsToUpload = readingDao.getNumberOfUnUploadedReadings()

        Log.d(
            TAG,
            "There are $numberOfPatientsToUpload patients and " +
                "$numberOfReadingsToUpload readings to upload"
        )

        val numberToUploadString = when {
            numberOfPatientsToUpload != 1 && numberOfReadingsToUpload != 1 &&
                (numberOfPatientsToUpload + numberOfReadingsToUpload != 0) -> {
                context.getString(
                    R.string.sync_activity_d_patients_and_d_readings_to_upload,
                    numberOfPatientsToUpload,
                    numberOfReadingsToUpload
                )
            }
            numberOfPatientsToUpload != 1 && numberOfReadingsToUpload == 1 -> {
                context.getString(
                    R.string.sync_activity_d_patients_and_d_reading_to_upload,
                    numberOfPatientsToUpload,
                    numberOfReadingsToUpload
                )
            }
            numberOfPatientsToUpload == 1 && numberOfReadingsToUpload != 1 -> {
                context.getString(
                    R.string.sync_activity_d_patient_and_d_readings_to_upload,
                    numberOfPatientsToUpload,
                    numberOfReadingsToUpload
                )
            }
            numberOfPatientsToUpload == 1 && numberOfReadingsToUpload == 1 -> {
                context.getString(
                    R.string.sync_activity_d_patient_and_d_reading_to_upload,
                    numberOfPatientsToUpload,
                    numberOfReadingsToUpload
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

    val isConnectedToInternet = NetworkAvailableLiveData(context)

    @MainThread
    fun startSyncing() {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
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
        private const val WORK_TAG = "Sync-UploadDownloadPatientsAndReadings"
        private const val WORK_NAME = "SyncWorkerUniqueSync"
        private const val LAST_SYNC_JOB_UUID = "lastSyncJobUuid"
    }
}
