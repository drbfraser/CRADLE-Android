package com.cradleplatform.neptune.viewmodel.patients

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.viewmodel.patients.EditPatientViewModel.SaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for [PatientUpdateDrugMedicalActivity].
 * Preserves form state (the text being edited) across configuration changes like screen rotation.
 */
@HiltViewModel
class PatientUpdateDrugMedicalViewModel @Inject constructor(
    private val patientManager: PatientManager,
    private val networkStateManager: NetworkStateManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_RECORD_TEXT = "record_text"
    }

    val isNetworkAvailable: LiveData<Boolean> =
        networkStateManager.getInternetConnectivityStatus()

    /** The current text in the edit field, preserved across rotation. */
    var recordText: String
        get() = savedStateHandle.get<String>(KEY_RECORD_TEXT) ?: ""
        set(value) { savedStateHandle[KEY_RECORD_TEXT] = value }

    suspend fun saveAndUploadPatient(patient: Patient, isDrugRecord: Boolean): SaveResult {
        return withContext(Dispatchers.Main) {
            if (isNetworkAvailable.value == true) {
                when (patientManager.updatePatientMedicalRecord(patient, isDrugRecord)) {
                    is NetworkResult.Success -> SaveResult.SavedAndUploaded
                    else -> SaveResult.ServerReject
                }
            } else {
                saveRecordOffline(patient, isDrugRecord)
            }
        }
    }

    private suspend fun saveRecordOffline(patient: Patient, isDrugRecord: Boolean): SaveResult {
        if (isDrugRecord) {
            patient.drugLastEdited = UnixTimestamp.now.toLong()
        } else {
            patient.medicalLastEdited = UnixTimestamp.now.toLong()
        }
        patientManager.add(patient)
        return SaveResult.SavedOffline
    }
}

