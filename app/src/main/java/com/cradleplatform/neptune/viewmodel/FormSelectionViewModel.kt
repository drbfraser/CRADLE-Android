package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FormSelectionViewModel @Inject constructor (
    private val formManager: FormManager
) : ViewModel() {

    var formVersionLiveData: MutableLiveData<Array<String>> = MutableLiveData()

    private val formClassList: LiveData<List<FormClassification>> =
        formManager.getLiveDataFormClassifications()

    val formTemplateListAsString: LiveData<Array<String>> by lazy {
        formClassList.map { formClassificationList ->
            formClassificationList.map { formClass ->
                formClass.formClassName
            }.toTypedArray()
        }
    }

    /**
     * perform a search for a set of [FormTemplate] by [formName],
     *
     * returns a [List] of [String]s for available languages.
     *
     * throws error if none is available, user should make sure that the inputs parameters is retrieved from
     * the sets of LiveData provided by this class.
     *
     * @param formName the name (in Classification) of a FormTemplate
     */
    private suspend fun getFormTemplateVersionsFromName(formName: String): List<String> {
        val formTemplateResults = formManager.searchForFormTemplateWithName(formName)

        if (formTemplateResults.size > 1) {
            error("Too many FormTemplate name matched when searching with name")
        } else if (formTemplateResults.isEmpty()) {
            error("Unable to match FormTemplate from name")
        }

        val formTemplate = formTemplateResults[0]

        return formTemplate.languageVersions()
    }

    /**
     * Populates the dropdown list for languages available for a specific [FormTemplate].
     *
     * This function is bound to when a user selects a formTemplate from the dropdown list for [FormTemplate]s
     */
    fun formTemplateChanged(formName: String) {
        viewModelScope.launch {
            val langVersions = getFormTemplateVersionsFromName(formName)
            formVersionLiveData.postValue(langVersions.toTypedArray())
        }
    }

    /**
     * returns [FormTemplate] that matches the input [formName]
     *
     * throws errors if not found, user should make sure that the inputs parameters is retrieved from
     * the sets of LiveData provided by this class.
     */
    fun getFormTemplateFromName(formName: String): FormTemplate {
        val currentFormClassList = formClassList.value
        if (currentFormClassList.isNullOrEmpty()) {
            error("Tried to retrieve FormTemplate by name when no FormClass is available")
        }
        return currentFormClassList.find { it.formClassName == formName }?.formTemplate ?:
            error("FormTemplate cannot be found in the current list. Please check if parameter is correct")
    }
}
