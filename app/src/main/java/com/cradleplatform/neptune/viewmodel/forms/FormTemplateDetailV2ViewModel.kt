package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormTemplateV2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standalone V2 form template detail screen, calling [RestApi.getFormTemplateV2] directly.
 * Read-only, like [FormTemplateListV2ViewModel] - no mapper, no local database involved.
 */
sealed class FormTemplateDetailV2State {
    object Loading : FormTemplateDetailV2State()
    data class Success(val template: FormTemplateV2) : FormTemplateDetailV2State()
    data class Error(val message: String) : FormTemplateDetailV2State()
}

@HiltViewModel
class FormTemplateDetailV2ViewModel @Inject constructor(
    private val restApi: RestApi,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val formTemplateId: String = requireNotNull(savedStateHandle[EXTRA_TEMPLATE_ID]) {
        "FormTemplateDetailV2Activity requires $EXTRA_TEMPLATE_ID as an Intent extra"
    }

    private val _state = MutableStateFlow<FormTemplateDetailV2State>(FormTemplateDetailV2State.Loading)
    val state: StateFlow<FormTemplateDetailV2State> = _state.asStateFlow()

    init {
        loadTemplate()
    }

    fun loadTemplate() {
        _state.value = FormTemplateDetailV2State.Loading
        viewModelScope.launch {
            _state.value = when (val result = restApi.getFormTemplateV2(formTemplateId)) {
                is NetworkResult.Success -> FormTemplateDetailV2State.Success(result.value)
                is NetworkResult.Failure -> FormTemplateDetailV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormTemplateDetailV2State.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE_ID = "form_template_v2_id"
    }
}
