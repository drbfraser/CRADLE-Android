package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormTemplateShallowV2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FormTemplateListV2State {
    object Loading : FormTemplateListV2State()
    data class Success(val templates: List<FormTemplateShallowV2>) : FormTemplateListV2State()
    data class Error(val message: String) : FormTemplateListV2State()
}

@HiltViewModel
class FormTemplateListV2ViewModel @Inject constructor(
    private val restApi: RestApi
) : ViewModel() {

    private val _state = MutableStateFlow<FormTemplateListV2State>(FormTemplateListV2State.Loading)
    val state: StateFlow<FormTemplateListV2State> = _state.asStateFlow()

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        _state.value = FormTemplateListV2State.Loading
        viewModelScope.launch {
            _state.value = when (val result = restApi.getAllFormTemplatesV2()) {
                is NetworkResult.Success -> FormTemplateListV2State.Success(result.value.templates)
                is NetworkResult.Failure -> FormTemplateListV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormTemplateListV2State.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }
}
