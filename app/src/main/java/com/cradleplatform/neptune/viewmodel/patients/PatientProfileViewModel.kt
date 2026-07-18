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
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Reading
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.model.WorkflowRow
import com.cradleplatform.neptune.model.WorkflowStepRow
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.utilities.DateUtil
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

    private val _submittedForms = MutableLiveData<List<FormResponse>>()
    val submittedForms: LiveData<List<FormResponse>> = _submittedForms

    private val _workflows = MutableLiveData<List<WorkflowRow>>()
    val workflows: LiveData<List<WorkflowRow>> = _workflows

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

        // Load submitted forms
        val submittedForms = formResponseManager.searchForSubmittedFormsByPatientId(patientId)
        _submittedForms.postValue(submittedForms ?: emptyList())

        val workflowsResult = patientManager.downloadWorkflowInstances(patientId)
        if (workflowsResult is NetworkResult.Success) {
            val rows = workflowsResult.value.map { instance ->
                val currentIndex = instance.currentStepId
                    ?.let { stepId -> instance.steps.indexOfFirst { it.id == stepId } }
                    ?.takeIf { it >= 0 }
                    ?: 0
                val currentInstanceStep = instance.steps.getOrNull(currentIndex)
                val currentStep = currentInstanceStep?.name ?: "N/A"
                val lastEdited = instance.lastEdited
                    ?.let { DateUtil.getDateStringFromTimestamp(it) }
                    ?: "N/A"
                val stepRows = instance.steps.map { step ->
                    WorkflowStepRow(
                        name = step.name,
                        status = step.status.orEmpty(),
                        startedDate = step.startDate
                            ?.let { DateUtil.getDateStringFromTimestamp(it) }
                            ?: "N/A",
                        completedDate = step.completionDate
                            ?.let { DateUtil.getDateStringFromTimestamp(it) }
                    )
                }
                WorkflowRow(
                    templateName = resolveTemplateName(instance.workflowTemplateId),
                    status = instance.status,
                    lastEdited = lastEdited,
                    stepCount = instance.steps.size,
                    currentStep = currentStep,
                    completedSteps = currentIndex,
                    instanceId = instance.id,
                    currentStepId = currentInstanceStep?.id,
                    currentStepActive =
                        currentInstanceStep?.status?.equals("Active", ignoreCase = true) == true,
                    steps = stepRows
                )
            }
            _workflows.postValue(rows)
        } else {
            _workflows.postValue(emptyList())
        }
    }

    private suspend fun resolveTemplateName(templateId: String?): String {
        if (templateId == null) return "N/A"
        val result = patientManager.downloadWorkflowTemplate(templateId)
        return if (result is NetworkResult.Success) {
            result.value.classification?.name ?: result.value.name ?: "N/A"
        } else {
            "N/A"
        }
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
