package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FormSubmissionDetailV2State {
    object Loading : FormSubmissionDetailV2State()
    data class Success(
        val submission: FormSubmissionWithAnswersV2,
        val template: FormTemplateV2
    ) : FormSubmissionDetailV2State()
    object Error : FormSubmissionDetailV2State()
}

@HiltViewModel
class FormSubmissionDetailV2ViewModel @Inject constructor(
    private val restApi: RestApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val submissionId: String = requireNotNull(savedStateHandle[EXTRA_SUBMISSION_ID])
    private val patientId: String = requireNotNull(savedStateHandle[EXTRA_PATIENT_ID])
    private val _state = MutableStateFlow<FormSubmissionDetailV2State>(FormSubmissionDetailV2State.Loading)
    val state: StateFlow<FormSubmissionDetailV2State> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadForm()
    }

    fun loadForm() {
        loadJob?.cancel()
        _state.value = FormSubmissionDetailV2State.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                readSubmissionAndTemplate()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                FormSubmissionDetailV2State.Error
            }
        }
    }

    private suspend fun readSubmissionAndTemplate(): FormSubmissionDetailV2State {
        val result = restApi.getFormSubmissionV2(submissionId)
        if (result !is NetworkResult.Success) return FormSubmissionDetailV2State.Error
        val submission = result.value
        if (submission.id != submissionId || submission.patientId != patientId) {
            return FormSubmissionDetailV2State.Error
        }
        // Use the submission's exact template ID, including unanswered questions and categories.
        return when (val template = restApi.getFormTemplateV2(submission.formTemplateId)) {
            is NetworkResult.Success -> FormSubmissionDetailV2State.Success(submission, template.value)
            else -> FormSubmissionDetailV2State.Error
        }
    }

    companion object {
        const val EXTRA_SUBMISSION_ID = "form_submission_v2_id"
        const val EXTRA_PATIENT_ID = "patient_id"
    }
}
