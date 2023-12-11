package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SavedFormsViewModel @Inject constructor(
    private val formResponseManager: FormResponseManager
) : ViewModel() {

    private suspend fun searchForFormResponsesByPatientId(id: String): List<FormResponse>? {
        return formResponseManager.searchForFormResponseByPatientId(id)
    }

    private suspend fun getFormTemplatesByPatientId(id: String): List<FormTemplate>? {
        return searchForFormResponsesByPatientId(id)?.map { it.formTemplate }
    }
}
