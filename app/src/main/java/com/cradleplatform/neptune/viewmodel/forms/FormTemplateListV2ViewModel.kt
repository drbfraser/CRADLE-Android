package com.cradleplatform.neptune.viewmodel.forms

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.model.FormTemplateShallowV2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val restApi: RestApi,
    private val sharedPreferences: SharedPreferences,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<FormTemplateListV2State>(FormTemplateListV2State.Loading)
    val state: StateFlow<FormTemplateListV2State> = _state.asStateFlow()

    private val _selectedTemplate = MutableStateFlow<FormTemplateDetailV2State?>(null)
    val selectedTemplate = _selectedTemplate.asStateFlow()
    val selectedTemplateId: String?
        get() = savedStateHandle["selected_template"]

    var selectedLanguage: String?
        get() = savedStateHandle["selected_language"]
        set(value) { savedStateHandle["selected_language"] = value }

    fun selectTemplate(id: String) {
        savedStateHandle["selected_template"] = id
        selectedLanguage = null
        _selectedTemplate.value = FormTemplateDetailV2State.Loading
        viewModelScope.launch {
            val result = restApi.getFormTemplateV2(id)
            if (savedStateHandle.get<String>("selected_template") != id) return@launch
            _selectedTemplate.value = when (result) {
                is NetworkResult.Success -> FormTemplateDetailV2State.Success(result.value)
                is NetworkResult.Failure -> FormTemplateDetailV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> FormTemplateDetailV2State.Error(result.cause.message ?: "Network error")
            }
        }
    }

    init {
        val language = selectedLanguage
        savedStateHandle.get<String>("selected_template")?.let { selectTemplate(it) }
        selectedLanguage = language
        loadTemplates()
    }

    fun loadTemplates() {
        _state.value = FormTemplateListV2State.Loading
        viewModelScope.launch {
            _state.value = when (val result = restApi.getAllFormTemplatesV2()) {
                is NetworkResult.Success -> {
                    cacheTemplates(result.value.templates)
                    FormTemplateListV2State.Success(result.value.templates)
                }
                is NetworkResult.Failure -> readCachedTemplates()
                    ?: FormTemplateListV2State.Error("Server error (${result.statusCode})")
                is NetworkResult.NetworkException -> readCachedTemplates()
                    ?: FormTemplateListV2State.Error(result.cause.message ?: "Network error")
            }
        }
    }

    private fun cacheTemplates(templates: List<FormTemplateShallowV2>) {
        sharedPreferences.edit { putString(CACHE_KEY, Gson().toJson(templates)) }
    }

    private fun readCachedTemplates(): FormTemplateListV2State.Success? {
        val json = sharedPreferences.getString(CACHE_KEY, null) ?: return null
        val type = object : TypeToken<List<FormTemplateShallowV2>>() {}.type
        val templates: List<FormTemplateShallowV2> = Gson().fromJson(json, type)
        return FormTemplateListV2State.Success(templates)
    }

    companion object {
        private const val CACHE_KEY = "v2-form-template-cached"
    }
}
