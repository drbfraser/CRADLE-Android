package com.cradleplatform.neptune.viewmodel.forms

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2AnswerState
import com.cradleplatform.neptune.model.FormV2SubmissionValidationResult
import com.cradleplatform.neptune.model.FormV2SubmissionValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
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

/** State of the V2 submission request. The Activity will render this in a separate UI step. */
sealed class FormV2SubmissionState {
    object Idle : FormV2SubmissionState()
    object Submitting : FormV2SubmissionState()
    object Submitted : FormV2SubmissionState()
    data class ValidationError(val questionId: String?, val message: String) : FormV2SubmissionState()
    data class Error(val message: String) : FormV2SubmissionState()
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
    private val _submissionState = MutableStateFlow<FormV2SubmissionState>(FormV2SubmissionState.Idle)
    val submissionState: StateFlow<FormV2SubmissionState> = _submissionState.asStateFlow()
    private val draftSaveMutex = Mutex()
    private var clearSavedDraftStatusJob: Job? = null
    private var hasSubmitted = false
    private var submissionId: String? = null

    val canSaveDraft: Boolean
        get() = !hasSubmitted && !patientId.isNullOrBlank() &&
            _state.value is FormV2RenderingState.Success

    val canSubmit: Boolean
        get() = !hasSubmitted && !patientId.isNullOrBlank() &&
            _state.value is FormV2RenderingState.Success &&
            _submissionState.value != FormV2SubmissionState.Submitting

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
            submissionId = draft.submissionId
        }
    }

    /** Saves only patient-based forms after their template has finished loading. */
    fun saveDraft() {
        // Avoid overwriting a stored draft before its answers finish restoring
        if (!canSaveDraft) return
        val patient = patientId?.takeIf { it.isNotBlank() } ?: return
        val template = loadedTemplate ?: return
        // Capture the answers at the save request, before a queued write or further edits
        val draft = createDraft(patient, template, answerState.toFormAnswers())
        clearSavedDraftStatusJob?.cancel()
        _draftSaveState.value = FormV2DraftSaveState.Saving
        viewModelScope.launch {
            // Keep manual and lifecycle saves in order
            draftSaveMutex.withLock {
                if (hasSubmitted) {
                    _draftSaveState.value = FormV2DraftSaveState.Idle
                    return@withLock
                }
                _draftSaveState.value = FormV2DraftSaveState.Saving
                try {
                    // The DAO preserves createdAt on updates. Success follows the completed transaction.
                    formV2DraftDao.upsert(draft)
                    _draftSaveState.value = FormV2DraftSaveState.Saved
                    clearSavedDraftStatusJob = viewModelScope.launch {
                        delay(DRAFT_SAVE_SUCCESS_VISIBLE_MILLIS)
                        if (_draftSaveState.value == FormV2DraftSaveState.Saved) {
                            _draftSaveState.value = FormV2DraftSaveState.Idle
                        }
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(javaClass.simpleName, "Failed to save V2 draft", exception)
                    _draftSaveState.value = FormV2DraftSaveState.Error
                }
            }
        }
    }

    /**
     * Validates and posts the current V2 answers. The Activity does not expose this action yet.
     * Only after successful response can remove the local draft
     */
    fun submitForm() {
        if (!canSubmit) return
        val patient = patientId?.takeIf { it.isNotBlank() } ?: return
        val template = loadedTemplate ?: return
        val draftAnswers = answerState.toFormAnswers()
        val answers = when (val validation = FormV2SubmissionValidator.validate(
            template,
            draftAnswers
        )) {
            is FormV2SubmissionValidationResult.Error -> {
                _submissionState.value = FormV2SubmissionState.ValidationError(
                    validation.questionId,
                    validation.message
                )
                return
            }
            is FormV2SubmissionValidationResult.Success -> validation.answers
        }

        _submissionState.value = FormV2SubmissionState.Submitting
        viewModelScope.launch {
            val requestId = try {
                persistSubmissionDraft(patient, template, draftAnswers)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(javaClass.simpleName, "Failed to save V2 form before submission", exception)
                _submissionState.value = FormV2SubmissionState.Error(
                    "Unable to save form before submission."
                )
                return@launch
            }
            val request = CreateFormSubmissionRequestV2(
                id = requestId,
                formTemplateId = template.id,
                patientId = patient,
                answers = answers
            )
            try {
                when (val result = restApi.postFormSubmissionV2(request)) {
                    is NetworkResult.Success -> deleteSubmittedDraft(patient, template.id)
                    is NetworkResult.Failure -> _submissionState.value = FormV2SubmissionState.Error(
                        "Server error (${result.statusCode})"
                    )
                    is NetworkResult.NetworkException -> _submissionState.value = FormV2SubmissionState.Error(
                        result.cause.message ?: "Network error"
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(javaClass.simpleName, "Failed to submit V2 form", exception)
                _submissionState.value = FormV2SubmissionState.Error(
                    exception.message ?: "Unable to submit form."
                )
            }
        }
    }

    private suspend fun persistSubmissionDraft(
        patientId: String,
        template: FormTemplateV2,
        answers: List<FormAnswerV2>
    ): String = draftSaveMutex.withLock {
        val requestId = submissionId ?: UUID.randomUUID().toString()
        formV2DraftDao.upsert(createDraft(patientId, template, answers, requestId))
        submissionId = requestId
        requestId
    }

    private suspend fun deleteSubmittedDraft(patientId: String, formTemplateId: String) {
        // Prevent a queued onStop() save from recreating a draft after a successful POST.
        hasSubmitted = true
        clearSavedDraftStatusJob?.cancel()
        try {
            draftSaveMutex.withLock {
                formV2DraftDao.deleteDraft(patientId, formTemplateId)
            }
            _submissionState.value = FormV2SubmissionState.Submitted
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(javaClass.simpleName, "Failed to remove submitted V2 draft", exception)
            _submissionState.value = FormV2SubmissionState.Error(
                "Form submitted, but the local draft could not be removed."
            )
        }
    }

    private fun createDraft(
        patientId: String,
        template: FormTemplateV2,
        answers: List<FormAnswerV2>,
        submissionId: String? = this.submissionId
    ): FormV2Draft {
        val now = System.currentTimeMillis()
        return FormV2Draft(
            patientId = patientId,
            formTemplateId = template.id,
            formTemplateVersion = template.version,
            formTemplate = template,
            answers = answers,
            createdAt = now,
            updatedAt = now,
            submissionId = submissionId
        )
    }

    private companion object {
        const val DRAFT_SAVE_SUCCESS_VISIBLE_MILLIS = 3_000L
    }
}
