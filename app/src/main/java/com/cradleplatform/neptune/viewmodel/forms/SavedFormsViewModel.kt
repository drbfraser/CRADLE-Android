package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.Patient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedFormsViewModel @Inject constructor(
    val formResponseManager: FormResponseManager,
    private val patientManager: PatientManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedFormsState())
    val uiState: StateFlow<SavedFormsState> = _uiState.asStateFlow()

    fun loadForms(patientId: String?, savedAsDraft: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, patientId = patientId, savedAsDraft = savedAsDraft) }

            formResponseManager.purgeOutdatedFormResponses()

            val formMap = mutableMapOf<Patient, MutableList<FormResponse>>()

            if (savedAsDraft) {
                if (patientId != null && patientId.isNotEmpty()) {
                    // Load draft forms for specific patient
                    val formList = formResponseManager.searchForDraftFormsByPatientId(patientId)
                    val patient = patientManager.getPatientById(patientId)
                    if (patient != null && formList != null) {
                        formMap[patient] = formList
                    }
                } else {
                    // Load all draft forms
                    val formList = formResponseManager.searchForDraftForms()
                    formList?.forEach { formResponse ->
                        val patient = patientManager.getPatientById(formResponse.patientId)
                        if (patient != null) {
                            if (formMap.containsKey(patient)) {
                                formMap[patient]?.add(formResponse)
                            } else {
                                formMap[patient] = mutableListOf(formResponse)
                            }
                        }
                    }
                }
            } else {
                // Load submitted forms for specific patient
                if (patientId != null) {
                    val formList = formResponseManager.searchForSubmittedFormsByPatientId(patientId)
                    val patient = patientManager.getPatientById(patientId)
                    if (patient != null && formList != null) {
                        formMap[patient] = formList
                    }
                }
            }

            _uiState.update { it.copy(formMap = formMap, isLoading = false) }
        }
    }

    suspend fun deleteFormResponse(formResponseId: Long) {
        formResponseManager.deleteFormResponseById(formResponseId)
    }
}
