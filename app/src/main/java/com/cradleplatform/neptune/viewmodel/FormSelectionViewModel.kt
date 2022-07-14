package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.FormTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FormSelectionViewModel @Inject constructor (
    private val formManager: FormManager
) : ViewModel() {

    var formVersionLiveData: MutableLiveData<Array<String>> = MutableLiveData()

    private val formTemplateList: LiveData<List<FormTemplate>> = formManager.getLiveDataFormTemplates()

    val formTemplateListAsString: LiveData<Array<String>> by lazy {
        formTemplateList.map { it.map(FormTemplate::name).distinct().toTypedArray() }
    }

    /**
     * perform a search for a set of [FormTemplate] by [formName],
     *
     * returns a [List] of [String]s for available languages.
     *
     * throws error if none is available, user should make sure that the inputs parameters is retrieved from
     * the sets of LiveData provided by this class.
     */
    private fun getFormTemplateVersionsFromName(formName: String): List<String> {
        val formTemplateVersions = formTemplateList.value!!.filter { it.name == formName }.map(FormTemplate::lang)
        if (formTemplateVersions.isEmpty()) {
            error("Unable to match FormTemplate from name when finding Versions")
        }
        return formTemplateVersions
    }

    /**
     * Populates the dropdown list for languages available for a specific [FormTemplate].
     *
     * This function is bound to when a user selects a formTemplate from the dropdown list for [FormTemplate]s
     */
    fun formTemplateChanged(formName: String) {
        formVersionLiveData.postValue(
            getFormTemplateVersionsFromName(formName).toTypedArray()
        )
    }

    /**
     * returns [FormTemplate] that matches the input [formName] and [version]
     *
     * throws errors if not found, user should make sure that the inputs parameters is retrieved from
     * the sets of LiveData provided by this class.
     */
    fun getFormTemplateFromNameAndVersion(formName: String, version: String): FormTemplate {
        val currentFormTemplateList = formTemplateList.value
        if (currentFormTemplateList.isNullOrEmpty()) {
            error("Tried to retrieve FormTemplate by name when no FormTemplate is available")
        }
        return currentFormTemplateList.find { it.name == formName && it.lang == version } ?:
            error("FormTemplate cannot be found in the current list. Please check if parameter is correct")
    }
}
