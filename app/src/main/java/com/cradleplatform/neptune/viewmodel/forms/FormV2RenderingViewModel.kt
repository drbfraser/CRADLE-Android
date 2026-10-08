package com.cradleplatform.neptune.viewmodel.forms

import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * UI state for loading and rendering a V2 form template.
 */
sealed class FormV2RenderingState {
    object Loading : FormV2RenderingState()
    data class Success(val template: FormTemplateV2) : FormV2RenderingState()
    data class Error(val message: String) : FormV2RenderingState()
}

/** State of the local V2 draft write */
sealed class FormV2DraftSaveState {
    object Idle : FormV2DraftSaveState()
    object Saving : FormV2DraftSaveState()
    object Saved : FormV2DraftSaveState()
    object Error : FormV2DraftSaveState()
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
    private val _draftSaveState = MutableStateFlow<FormV2DraftSaveState>(FormV2DraftSaveState.Idle)
    val draftSaveState: StateFlow<FormV2DraftSaveState> = _draftSaveState.asStateFlow()
    private val draftSaveMutex = Mutex()

    val canSaveDraft: Boolean
        get() = !patientId.isNullOrBlank() && _state.value is FormV2RenderingState.Success

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
        // Avoid overwriting a stored draft before its answers finish restoring
        if (!canSaveDraft) return
        val patient = patientId?.takeIf { it.isNotBlank() } ?: return
        val template = loadedTemplate ?: return
        val now = System.currentTimeMillis()
        // Capture the answers at the save request, before a queued write or further edits
        val draft = FormV2Draft(
            patientId = patient,
            formTemplateId = template.id,
            formTemplateVersion = template.version,
            formTemplate = template,
            answers = answerState.toFormAnswers(),
            createdAt = now,
            updatedAt = now
        )
        _draftSaveState.value = FormV2DraftSaveState.Saving
        viewModelScope.launch {
            // Keep manual and lifecycle saves in order
            draftSaveMutex.withLock {
                _draftSaveState.value = FormV2DraftSaveState.Saving
                try {
                    // The DAO preserves createdAt on updates. Success follows the completed transaction.
                    formV2DraftDao.upsert(draft)
                    _draftSaveState.value = FormV2DraftSaveState.Saved
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(javaClass.simpleName, "Failed to save V2 draft", exception)
                    _draftSaveState.value = FormV2DraftSaveState.Error
                }
            }
        }
    }
}
