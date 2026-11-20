package com.cradleplatform.neptune.viewmodel.forms

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.activities.forms.FormTemplateListV2Activity
import com.cradleplatform.neptune.activities.forms.FormV2RenderingActivity
import com.cradleplatform.neptune.database.daos.FormV2DraftDao
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.FormTemplateV2Manager
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormV2Draft
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.FormV2AnswerState
import com.cradleplatform.neptune.model.FormV2SubmissionComparison
import com.cradleplatform.neptune.model.FormV2SubmissionValidationResult
import com.cradleplatform.neptune.model.FormV2SubmissionValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
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

sealed class FormV2DraftDeleteState {
    object Idle : FormV2DraftDeleteState()
    object Deleting : FormV2DraftDeleteState()
    object Deleted : FormV2DraftDeleteState()
    object Error : FormV2DraftDeleteState()
}

/** State of the V2 submission request, separate from template loading and draft saving */
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
    private val formTemplateManager: FormTemplateV2Manager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val formTemplateId: String = requireNotNull(
        savedStateHandle[FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID]
    ) {
        "FormV2RenderingActivity requires ${FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID} as an Intent extra"
    }

    private val patientId: String? = savedStateHandle[FormTemplateListV2Activity.EXTRA_PATIENT_ID]
    private val requestedLanguage: String? = savedStateHandle.get<String>(FormV2RenderingActivity.EXTRA_LANGUAGE)
        ?.takeIf { it.isNotBlank() }
    private var selectedLanguage: String = requestedLanguage ?: "English"
    val language: String
        get() = selectedLanguage
    private var loadedTemplate: FormTemplateV2? = null

    private val _state = MutableStateFlow<FormV2RenderingState>(FormV2RenderingState.Loading)
    val state: StateFlow<FormV2RenderingState> = _state.asStateFlow()
    val answerState = FormV2AnswerState()
    private val _draftSaveState = MutableStateFlow<FormV2DraftSaveState>(FormV2DraftSaveState.Idle)
    val draftSaveState: StateFlow<FormV2DraftSaveState> = _draftSaveState.asStateFlow()
    private val _draftDeleteState = MutableStateFlow<FormV2DraftDeleteState>(FormV2DraftDeleteState.Idle)
    val draftDeleteState: StateFlow<FormV2DraftDeleteState> = _draftDeleteState.asStateFlow()
    private val _submissionState = MutableStateFlow<FormV2SubmissionState>(FormV2SubmissionState.Idle)
    val submissionState: StateFlow<FormV2SubmissionState> = _submissionState.asStateFlow()
    private val _submissionErrors = Channel<FormV2SubmissionErrorType>(Channel.BUFFERED)
    val submissionErrors = _submissionErrors.receiveAsFlow()
    private val draftSaveMutex = Mutex()
    private var clearSavedDraftStatusJob: Job? = null
    private var hasSubmitted = false
    private var submissionId: String? = null
    private var savedAnswers: Map<String, AnswerV2> = emptyMap()
    private var savedLanguage: String = selectedLanguage
    var hasSavedDraft: Boolean = false
        private set
    private val hasFinishedForm: Boolean
        get() = hasSubmitted || _draftDeleteState.value == FormV2DraftDeleteState.Deleted

    val hasUnsavedChanges: Boolean
        get() = !hasFinishedForm && _state.value is FormV2RenderingState.Success &&
            (answerState.toFormAnswers().associate { it.questionId to it.answer } != savedAnswers ||
                selectedLanguage != savedLanguage)

    val canSaveDraft: Boolean
        get() = !hasFinishedForm && _draftDeleteState.value != FormV2DraftDeleteState.Deleting &&
            !patientId.isNullOrBlank() &&
            _state.value is FormV2RenderingState.Success

    val canSubmit: Boolean
        get() = canSaveDraft &&
            _submissionState.value != FormV2SubmissionState.Submitting

    val canEditAnswers: Boolean
        get() = !hasFinishedForm && _draftDeleteState.value != FormV2DraftDeleteState.Deleting &&
            _submissionState.value != FormV2SubmissionState.Submitting

    val canDeleteDraft: Boolean
        get() = hasSavedDraft && canSubmit && _draftSaveState.value != FormV2DraftSaveState.Saving

    init {
        loadTemplate()
    }

    private fun loadTemplate() {
        viewModelScope.launch {
            val savedDraft = try {
                patientId?.let { formV2DraftDao.getDraft(it, formTemplateId) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(javaClass.simpleName, "Failed to load V2 draft", exception)
                _state.value = FormV2RenderingState.Error("Unable to load saved form.")
                return@launch
            }

            if (savedDraft != null) {
                loadedTemplate = savedDraft.formTemplate
                restoreDraft(savedDraft)
                _state.value = FormV2RenderingState.Success(savedDraft.formTemplate)
                return@launch
            }

            _state.value = when (val result = formTemplateManager.getTemplate(formTemplateId)) {
                is NetworkResult.Success -> {
                    loadedTemplate = result.value
                    FormV2RenderingState.Success(result.value)
                }
                is NetworkResult.Failure -> FormV2RenderingState.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormV2RenderingState.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }

    private fun restoreDraft(draft: FormV2Draft) {
        answerState.replaceAnswers(draft.answers)
        submissionId = draft.submissionId
        // Keep the saved language unless the user explicitly selected one for this form.
        selectedLanguage = requestedLanguage ?: draft.language
        rememberSavedDraft(draft)
    }

    /** Saves a patient draft and returns the write job so navigation can wait for it. */
    fun saveDraft(): Job? {
        // Avoid overwriting a stored draft before its answers finish restoring
        if (!canSaveDraft) return null
        val patient = patientId?.takeIf { it.isNotBlank() } ?: return null
        val template = loadedTemplate ?: return null
        // Capture the answers at the save request, before a queued write or further edits
        val draft = createDraft(patient, template, answerState.toFormAnswers())
        clearSavedDraftStatusJob?.cancel()
        _draftSaveState.value = FormV2DraftSaveState.Saving
        return viewModelScope.launch {
            // Keep manual and lifecycle saves in order
            draftSaveMutex.withLock {
                if (hasFinishedForm) {
                    _draftSaveState.value = FormV2DraftSaveState.Idle
                    return@withLock
                }
                _draftSaveState.value = FormV2DraftSaveState.Saving
                try {
                    // The DAO preserves createdAt on updates. Success follows the completed transaction.
                    val currentDraft = draft.copy(submissionId = submissionId)
                    formV2DraftDao.upsert(currentDraft)
                    rememberSavedDraft(currentDraft)
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

    fun revalidateQuestion(questionId: String?) {
        val error = _submissionState.value as? FormV2SubmissionState.ValidationError ?: return
        if (questionId == null || error.questionId != questionId) return
        val template = (_state.value as? FormV2RenderingState.Success)?.template ?: return
        val question = template.questions.orEmpty().firstOrNull { it.id == questionId } ?: return
        val result = FormV2SubmissionValidator.validate(
            template.copy(questions = listOf(question)),
            answerState.toFormAnswers().filter { it.questionId == questionId }
        )
        _submissionState.value = when (result) {
            is FormV2SubmissionValidationResult.Success -> FormV2SubmissionState.Idle
            is FormV2SubmissionValidationResult.Error ->
                error.copy(questionId = result.questionId, message = result.message)
        }
    }

    fun deleteDraft(): Job? {
        if (!canDeleteDraft) return null
        val patient = patientId ?: return null
        _draftDeleteState.value = FormV2DraftDeleteState.Deleting
        return viewModelScope.launch {
            draftSaveMutex.withLock {
                try {
                    // Serialize deletion with saves so an older write cannot recreate the draft.
                    formV2DraftDao.deleteDraft(patient, formTemplateId)
                    hasSavedDraft = false
                    clearSavedDraftStatusJob?.cancel()
                    _draftDeleteState.value = FormV2DraftDeleteState.Deleted
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(javaClass.simpleName, "Failed to delete V2 draft", exception)
                    _draftDeleteState.value = FormV2DraftDeleteState.Error
                }
            }
        }
    }

    /** Validates the current answers, saves a local draft, and submits the form. */
    fun submitForm() {
        if (!canSubmit) return
        val patient = patientId?.takeIf { it.isNotBlank() } ?: return
        val template = loadedTemplate ?: return
        val draftAnswers = answerState.toFormAnswers()
        val retrying = submissionId != null
        val newAnswers = if (!retrying) validateAnswers(template, draftAnswers) ?: return else null

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
            try {
                val submissionLanguage = resolveSubmissionLanguage(template.id) ?: return@launch
                if (selectedLanguage != submissionLanguage) {
                    selectedLanguage = submissionLanguage
                    persistSubmissionDraft(patient, template, draftAnswers)
                }
                val request = CreateFormSubmissionRequestV2(
                    id = requestId,
                    formTemplateId = template.id,
                    patientId = patient,
                    lang = submissionLanguage,
                    answers = draftAnswers
                )
                // An accepted date can expire before a lost response is recovered.
                if (retrying && verifyExistingSubmission(request, template, allowMissing = true)) return@launch
                val answers = newAnswers ?: validateAnswers(template, draftAnswers) ?: return@launch
                when (val result = restApi.postFormSubmissionV2(request.copy(answers = answers))) {
                    is NetworkResult.Success -> deleteSubmittedDraft(patient, template.id)
                    is NetworkResult.Failure -> {
                        if (result.statusCode == HTTP_CONFLICT) {
                            verifyExistingSubmission(request, template)
                        } else {
                            reportHttpSubmissionError(result.statusCode)
                        }
                    }
                    is NetworkResult.NetworkException -> reportSubmissionError(
                        result.cause.message ?: "Network error", FormV2SubmissionErrorType.NETWORK_ERROR
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: IOException) {
                reportSubmissionError(exception.message ?: "Network error", FormV2SubmissionErrorType.NETWORK_ERROR)
            } catch (exception: Exception) {
                Log.e(javaClass.simpleName, "Failed to submit V2 form", exception)
                reportSubmissionError(
                    exception.message ?: "Unable to submit form.", FormV2SubmissionErrorType.SERVER_ERROR
                )
            }
        }
    }

    private fun validateAnswers(template: FormTemplateV2, answers: List<FormAnswerV2>): List<FormAnswerV2>? =
        when (val validation = FormV2SubmissionValidator.validate(template, answers)) {
            is FormV2SubmissionValidationResult.Success -> validation.answers
            is FormV2SubmissionValidationResult.Error -> {
                _submissionState.value = FormV2SubmissionState.ValidationError(
                    validation.questionId, validation.message
                )
                null
            }
        }

    /** False permits a fresh POST only after a preflight GET explicitly returns 404. */
    private suspend fun verifyExistingSubmission(
        request: CreateFormSubmissionRequestV2,
        template: FormTemplateV2,
        allowMissing: Boolean = false
    ): Boolean =
        when (val result = restApi.getFormSubmissionV2(requireNotNull(request.id))) {
            is NetworkResult.Success -> {
                // Normalize stored dates without applying today's restrictions to a historical submission.
                val comparisonTemplate = template.copy(questions = template.questions?.map {
                    it.copy(allowPastDates = null, allowFutureDates = null)
                })
                val answers = validateAnswers(comparisonTemplate, request.answers)
                if (answers != null &&
                    FormV2SubmissionComparison.matches(request.copy(answers = answers), result.value)
                ) {
                    deleteSubmittedDraft(request.patientId, request.formTemplateId)
                } else if (answers != null) {
                    _submissionState.value = FormV2SubmissionState.Error(
                        "The server form differs from this draft. Your draft was kept; no update was sent."
                    )
                }
                true
            }
            is NetworkResult.Failure -> {
                val canCreate = allowMissing && result.statusCode == HTTP_NOT_FOUND
                if (!canCreate) reportHttpSubmissionError(result.statusCode)
                !canCreate
            }
            is NetworkResult.NetworkException -> throw result.cause
        }

    private suspend fun resolveSubmissionLanguage(templateId: String): String? {
        val languages = when (val result = restApi.getFormTemplateLanguagesV2(templateId)) {
            is NetworkResult.Success -> result.value.languages
            is NetworkResult.Failure -> {
                reportHttpSubmissionError(result.statusCode)
                return null
            }
            is NetworkResult.NetworkException -> throw result.cause
        }
        return languages.firstOrNull { it == language }
            ?: languages.firstOrNull { it.equals(language, ignoreCase = true) }
            ?: error("The selected language is no longer available for this form.")
    }

    private suspend fun reportHttpSubmissionError(statusCode: Int) {
        val errorType = when (statusCode) {
            401 -> FormV2SubmissionErrorType.AUTH_ERROR
            403 -> FormV2SubmissionErrorType.PERMISSION_ERROR
            else -> FormV2SubmissionErrorType.SERVER_ERROR
        }
        reportSubmissionError("Server error ($statusCode)", errorType)
    }

    private suspend fun reportSubmissionError(message: String, errorType: FormV2SubmissionErrorType) {
        _submissionState.value = FormV2SubmissionState.Error(message)
        _submissionErrors.send(errorType)
    }

    private suspend fun persistSubmissionDraft(
        patientId: String,
        template: FormTemplateV2,
        answers: List<FormAnswerV2>
    ): String = draftSaveMutex.withLock {
        val requestId = submissionId ?: UUID.randomUUID().toString()
        val draft = createDraft(patientId, template, answers, requestId)
        formV2DraftDao.upsert(draft)
        rememberSavedDraft(draft)
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
                hasSavedDraft = false
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

    private fun rememberSavedDraft(draft: FormV2Draft) {
        hasSavedDraft = true
        savedAnswers = draft.answers.associate { it.questionId to it.answer }
        savedLanguage = draft.language
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
            language = selectedLanguage,
            createdAt = now,
            updatedAt = now,
            submissionId = submissionId
        )
    }

    private companion object {
        const val DRAFT_SAVE_SUCCESS_VISIBLE_MILLIS = 3_000L
        const val HTTP_CONFLICT = 409
        const val HTTP_NOT_FOUND = 404
    }
}
