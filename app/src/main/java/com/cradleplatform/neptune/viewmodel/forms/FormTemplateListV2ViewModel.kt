package com.cradleplatform.neptune.viewmodel.forms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.manager.FormTemplateV2Manager
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
    private val formTemplateManager: FormTemplateV2Manager
) : ViewModel() {

    private val _state = MutableStateFlow<FormTemplateListV2State>(FormTemplateListV2State.Loading)
    val state: StateFlow<FormTemplateListV2State> = _state.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<FormTemplateDetailV2State?>(null)
    val selectedTemplate = _selectedTemplate.asStateFlow()
    var selectedTemplateId: String? = null
        private set
    var selectedLanguage: String? = null

    fun selectTemplate(id: String) {
        selectedTemplateId = id
        selectedLanguage = null
        _selectedTemplate.value = FormTemplateDetailV2State.Loading
        viewModelScope.launch {
            val result = formTemplateManager.getTemplate(id)
            if (selectedTemplateId != id) return@launch
            _selectedTemplate.value = when (result) {
                is NetworkResult.Success -> FormTemplateDetailV2State.Success(result.value)
                is NetworkResult.Failure -> FormTemplateDetailV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormTemplateDetailV2State.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        _state.value = FormTemplateListV2State.Loading
        viewModelScope.launch {
            _state.value = when (val result = formTemplateManager.getAvailableTemplates()) {
                is NetworkResult.Success -> FormTemplateListV2State.Success(result.value)
                is NetworkResult.Failure -> FormTemplateListV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormTemplateListV2State.Error(
                    result.cause.message ?: "Network error"
                )
            }
        }
    }
}
