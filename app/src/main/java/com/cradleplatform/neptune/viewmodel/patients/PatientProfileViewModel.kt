package com.cradleplatform.neptune.viewmodel.patients

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.model.Assessment
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    private val patientManager: PatientManager,
    private val readingManager: ReadingManager,
    private val referralManager: ReferralManager,
    private val assessmentManager: AssessmentManager,
    private val formResponseManager: FormResponseManager
) : ViewModel() {

    private val _patient = MutableLiveData<Patient?>()
    val patient: LiveData<Patient?> = _patient

    private val _readings = MutableLiveData<List<Reading>>()
    val readings: LiveData<List<Reading>> = _readings

    private val _referrals = MutableLiveData<List<Referral>>()
    val referrals: LiveData<List<Referral>> = _referrals

    private val _assessments = MutableLiveData<List<Assessment>>()
    val assessments: LiveData<List<Assessment>> = _assessments

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _savedFormCount = MutableLiveData<Int>()
    val savedFormCount: LiveData<Int> = _savedFormCount

    private val _submittedFormCount = MutableLiveData<Int>()
    val submittedFormCount: LiveData<Int> = _submittedFormCount

    /**
     * Load patient data by ID
     */
    fun loadPatient(patientId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val patient = patientManager.getPatientById(patientId)
                _patient.value = patient

                if (patient != null) {
                    loadPatientRelatedData(patientId)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Set patient directly (when passed as intent extra)
     */
    fun setPatient(patient: Patient) {
        _patient.value = patient
        viewModelScope.launch {
            loadPatientRelatedData(patient.id)
        }
    }

    /**
     * Load readings, referrals, and assessments for the patient
     */
    private suspend fun loadPatientRelatedData(patientId: String) {
        // Load readings
        val readings = readingManager.getReadingsByPatientId(patientId)
        _readings.postValue(readings)

        // Load referrals
        val referrals = referralManager.getReferralByPatientId(patientId)
        _referrals.postValue(referrals ?: emptyList())

        // Load assessments
        val assessments = assessmentManager.getAssessmentByPatientId(patientId)
        _assessments.postValue(assessments ?: emptyList())
    }

    /**
     * Refresh patient data
     */
    fun refreshPatientData() {
        _patient.value?.let { patient ->
            viewModelScope.launch {
                val updatedPatient = patientManager.getPatientById(patient.id)
                _patient.postValue(updatedPatient)
                loadPatientRelatedData(patient.id)
            }
        }
    }

    /**
     * Load form counts for the patient
     */
    fun loadFormCounts(patientId: String) {
        viewModelScope.launch {
            val savedCount = formResponseManager.searchForDraftFormsByPatientId(patientId)?.size ?: 0
            val submittedCount = formResponseManager.searchForSubmittedFormsByPatientId(patientId)?.size ?: 0
            _savedFormCount.postValue(savedCount)
            _submittedFormCount.postValue(submittedCount)
        }
    }

    /**
     * Update patient pregnancy status
     */
    fun updatePatient(patient: Patient) {
        viewModelScope.launch {
            patientManager.add(patient)
            _patient.postValue(patient)
        }
    }
}

