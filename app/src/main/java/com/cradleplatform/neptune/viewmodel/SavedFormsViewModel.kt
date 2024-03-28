package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedFormsViewModel @Inject constructor(
    val formResponseManager: FormResponseManager,
    private val patientManager: PatientManager
) : ViewModel() {

    suspend fun purgeOutdatedFormResponses(): Int =
        formResponseManager.purgeOutdatedFormResponses()

    suspend fun searchForFormResponsesByPatientId(id: String): MutableList<FormResponse>? {
        return formResponseManager.searchForFormResponseByPatientId(id)
    }

    suspend fun addFormResponse(formResponse: FormResponse) {
        formResponseManager.updateOrInsertIfNotExistsFormResponse(formResponse)
    }

    private suspend fun getFormTemplatesByPatientId(id: String): List<FormTemplate>? {
        return searchForFormResponsesByPatientId(id)?.map { it.formTemplate }
    }

    suspend fun getPatientByPatientId(patientId: String): Patient? =
        patientManager.getPatientById(patientId)

    suspend fun searchForSubmittedFormsByPatientId(id: String): MutableList<FormResponse>? =
        formResponseManager.searchForSubmittedFormsByPatientId(id)

    suspend fun searchForDraftFormsByPatientId(id: String): MutableList<FormResponse>? =
        formResponseManager.searchForDraftFormsByPatientId(id)
}
