package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormSubmissionV2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SubmittedFormsV2State {
    object Loading : SubmittedFormsV2State()
    data class Success(val forms: List<FormSubmissionV2>) : SubmittedFormsV2State()
    object Error : SubmittedFormsV2State()
}

@HiltViewModel
class SubmittedFormsV2ViewModel @Inject constructor(
    private val restApi: RestApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val patientId: String = requireNotNull(savedStateHandle[EXTRA_PATIENT_ID])
    private val _state = MutableStateFlow<SubmittedFormsV2State>(SubmittedFormsV2State.Loading)
    val state: StateFlow<SubmittedFormsV2State> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadForms()
    }

    fun loadForms() {
        loadJob?.cancel()
        _state.value = SubmittedFormsV2State.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                when (val result = restApi.getPatientFormSubmissionsV2(patientId)) {
                    is NetworkResult.Success -> SubmittedFormsV2State.Success(
                        result.value.sortedByDescending { it.dateSubmitted }
                    )
                    else -> SubmittedFormsV2State.Error
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                SubmittedFormsV2State.Error
            }
        }
    }

    companion object {
        const val EXTRA_PATIENT_ID = "patient_id"
    }
}
