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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.GestationalAge
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.sync.SyncWorker
import com.cradleplatform.neptune.utilities.LiveDataDynamicModelBuilder
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [EditPatientInfoActivity]
 */
@HiltViewModel
class EditPatientViewModel @Inject constructor(
    private val patientManager: PatientManager,
    private val sharedPreferences: SharedPreferences,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * The [UUID] for the current unique syncing job.
     */
    private val currentWorkUuid = MutableLiveData<UUID?>()

    // make this for the live patient?
    val syncStatus: LiveData<WorkInfo?>
        get() = Transformations.switchMap(currentWorkUuid) { uuid ->
            uuid?.let { workManager.getWorkInfoByIdLiveData(it) }
        }


    /*val patientName: LiveData<String> = MediatorLiveData<String>().apply {
        addSource(syncStatus) {
            if (it?.state?.isFinished == true) {
                value = ""
            } else {
                value = ""
            }
        }
    }*/

    val isConnectedToInternet = NetworkAvailableLiveData(context)

    private val patientBuilder = LiveDataDynamicModelBuilder()

    suspend fun decompose(patientId: String) = withContext(Dispatchers.Main) {
        val patient = patientManager.getPatientById(patientId)
            ?: error("no patient with given id")
        patientBuilder.decompose(patient)
    }

    private val _patientIsExactDob: MediatorLiveData<Boolean?> =
        patientBuilder.get(Patient::isExactDob, defaultValue = false)
    val patientIsExactDob: LiveData<Boolean?> = _patientIsExactDob

    /* Patient Info */
    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientId: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::id)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientName: MutableLiveData<String>
        get() = patientBuilder.get<String>(Patient::name)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientDob: MediatorLiveData<String?> = patientBuilder.get<String?>(Patient::dob)

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     */
    val patientAge = MutableLiveData<Int?>(null)

    /**
     * Implicitly used in two-way Data Binding with PatientInfoFragment.
     * This listens to [patientGestationalAgeInput] and [patientGestationalAgeUnits].
     *
     * @see LiveDataInitializationManager.setupGestationAgeLiveData
     */
    val patientGestationalAge: MediatorLiveData<GestationalAge?> =
        patientBuilder.get<GestationalAge?>(Patient::gestationalAge)

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     * Input that is taken directly from the form as a String.
     * Can hold either an integer or a Double (for months input).
     */
    val patientGestationalAgeInput: MutableLiveData<String> = MediatorLiveData<String>()

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientGestationalAgeUnits: MutableLiveData<String> = MediatorLiveData<String>()

    /**
     * Used in two-way Data Binding with PatientInfoFragment
     * @see com.cradleplatform.neptune.binding.Converter.sexToString
     */
    val patientSex: MutableLiveData<Sex?>
        get() = patientBuilder.get<Sex?>(Patient::sex)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientIsPregnant: MutableLiveData<Boolean?>
        get() = patientBuilder.get<Boolean?>(Patient::isPregnant)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientZone: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::zone)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientHouseholdNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::householdNumber)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientVillageNumber: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::villageNumber)

    /** Used in two-way Data Binding with PatientInfoFragment */
    val patientAllergies: MutableLiveData<String?>
        get() = patientBuilder.get<String?>(Patient::allergy)

    val patientLastEdited: MutableLiveData<Long?>
        get() = patientBuilder.get<Long?>(Patient::lastEdited)

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
