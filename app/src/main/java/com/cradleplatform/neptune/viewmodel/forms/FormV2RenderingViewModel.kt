package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2AnswerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for loading and rendering a V2 form template.
 */
sealed class FormV2RenderingState {
    object Loading : FormV2RenderingState()
    data class Success(val template: FormTemplateV2) : FormV2RenderingState()
    data class Error(val message: String) : FormV2RenderingState()
}

@HiltViewModel
class FormV2RenderingViewModel @Inject constructor(
    private val restApi: RestApi,
    private val formV2DraftDao: FormV2DraftDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val formTemplateId: String = requireNotNull(
        savedStateHandle[FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID]
    ) {
        "FormV2RenderingActivity requires ${FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID} as an Intent extra"
    }

    private val patientId: String? = savedStateHandle[FormTemplateListV2Activity.EXTRA_PATIENT_ID]
    private var loadedTemplate: FormTemplateV2? = null

    private val _state = MutableStateFlow<FormV2RenderingState>(FormV2RenderingState.Loading)
    val state: StateFlow<FormV2RenderingState> = _state.asStateFlow()
    val answerState = FormV2AnswerState()

    init {
        loadTemplate()
    }

    private fun loadTemplate() {
        viewModelScope.launch {
            _state.value = when (val result = restApi.getFormTemplateV2(formTemplateId)) {
                is NetworkResult.Success -> {
                    loadedTemplate = result.value
                    restoreDraft(result.value)
                    FormV2RenderingState.Success(result.value)
                }
                is NetworkResult.Failure -> FormV2RenderingState.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormV2RenderingState.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }

    private suspend fun restoreDraft(template: FormTemplateV2) {
        val draft = patientId?.let { formV2DraftDao.getDraft(it, formTemplateId) }
        // Do not apply answers to a newer template whose questions may have changed.
        if (draft?.formTemplateVersion == template.version) {
            answerState.replaceAnswers(draft.answers)
        }
    }

    /** Saves only patient-based forms after their template has finished loading. */
    fun saveDraft() {
        val patient = patientId ?: return
        val template = loadedTemplate ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // The DAO keeps the existing createdAt value when this key already has a draft.
            formV2DraftDao.upsert(
                FormV2Draft(
                    patientId = patient,
                    formTemplateId = template.id,
                    formTemplateVersion = template.version,
                    formTemplate = template,
                    answers = answerState.toFormAnswers(),
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}
