package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.model.FormTemplate
import com.google.gson.Gson

class FormSelectionViewModel : ViewModel() {

    var formVersionLiveData: MutableLiveData<Array<String>> = MutableLiveData()

    private val formTemplateList: LiveData<List<FormTemplate>> = getLiveDataFormList()

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

// TODO: replace this function and hardcoded strings with accessing local(Synced) FormTemplates (refer to issue #55)
private fun getLiveDataFormList(): LiveData<List<FormTemplate>> {
    val tempFormTemplate: FormTemplate = Gson().fromJson(FORM_SELECTION_TEMP_FORM, FormTemplate::class.java)
    val tempFormTemplate2 = tempFormTemplate.copy(name = "FormTemplate2")
    val tempFormShort: FormTemplate = Gson().fromJson(FORM_SELECTION_TEMP_FORM_SHORT, FormTemplate::class.java)

    return MutableLiveData(
        listOf(
            tempFormTemplate,
            tempFormTemplate.copy(lang = "Latin"),
            tempFormTemplate2,
            tempFormShort.copy(lang = "japanese")
        )
    )
}

const val FORM_SELECTION_TEMP_FORM = """
{
    "lastEdited": 1650781303,
    "version": "V1",
    "name": "Ministry of Health and Sanitation Referral Form",
    "dateCreated": 1650781303,
    "category": "L1 referral",
    "id": "da36f2c0-2c14-4076-85aa-63990ad9a037",
    "lang": "english",
    "questions": [
    {
        "questionText": "Referred by",
        "questionType": "CATEGORY",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "id": "1c72dc3a-ae1f-43c7-bd8f-ec571e0f0d6e",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 0,
        "questionId": "category-referred-by"
    },
    {
        "questionText": "name/ID",
        "questionType": "STRING",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 0,
        "id": "6922532b-dce3-4423-906b-a0705968445f",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 1,
        "questionId": "referred-by-name"
    },
    {
        "questionText": "Initiating Facility",
        "questionType": "CATEGORY",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "id": "3ef19573-ff3e-4eb6-b8a4-216536b0628d",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 2,
        "questionId": "category-initiating-facility"
    },
    {
        "questionText": "Name",
        "questionType": "MULTIPLE_CHOICE",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 2,
        "id": "b1a1ac5c-9b22-41e0-b477-e6e505ca6b71",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [
        {
            "mcid": 0,
            "opt": "H000"
        },
        {
            "mcid": 1,
            "opt": "H001"
        }
        ],
        "questionIndex": 3,
        "questionId": "initiating-facility-name"
    },
    {
        "questionText": "Date of referral",
        "questionType": "DATE",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 2,
        "id": "b6ef8d85-9f9e-4cd4-8fad-f171d1c0362b",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 4,
        "questionId": "initiating-facility-date"
    },
    {
        "questionText": "Patient Info",
        "questionType": "CATEGORY",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "id": "b2ce6383-121b-46bb-9a46-4ea989c6a503",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 5,
        "questionId": "category-patient-info"
    },
    {
        "questionText": "Patient Name",
        "questionType": "STRING",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 5,
        "id": "05d67b26-c2e5-42b8-a8a4-ad115ac42f31",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 6,
        "questionId": "patient-name"
    },
    {
        "questionText": "Card Number",
        "questionType": "STRING",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 5,
        "id": "15b9bf3a-9c6d-44b1-8669-dce8bb73ff1d",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 7,
        "questionId": "patient-card-number"
    },
    {
        "numMin": 0.0,
        "questionText": "Heart Rate",
        "numMax": 220.0,
        "questionType": "INTEGER",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 5,
        "id": "92b40820-128c-48bd-9a75-ac08c09da0dc",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 8,
        "questionId": "patient-heart-rate"
    },
    {
        "questionText": "Whether the assessment is available?",
        "questionType": "MULTIPLE_CHOICE",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 5,
        "id": "14d91339-99e5-4304-898b-a60086b896d1",
        "visibleCondition": [],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [
        {
            "mcid": 0,
            "opt": "yes"
        },
        {
            "mcid": 1,
            "opt": "no"
        }
        ],
        "questionIndex": 9,
        "questionId": "patient-assessment-available"
    },
    {
        "questionText": "assessment",
        "questionType": "CATEGORY",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "id": "e816e65e-d1d4-4e89-9190-981e72c56634",
        "visibleCondition": [
        {
            "answers": {
            "mcidArray": [
            0
            ]
        },
            "qidx": 9,
            "relation": "EQUAL_TO"
        }
        ],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 10,
        "questionId": "patient-assessment"
    },
    {
        "questionText": "Final Diagnosis",
        "questionType": "STRING",
        "hasCommentAttached": false,
        "answers": {},
        "required": true,
        "categoryIndex": 10,
        "id": "474ce497-9a36-4904-81a8-d3adcb19449a",
        "visibleCondition": [
        {
            "answers": {
            "mcidArray": [
            0
            ]
        },
            "qidx": 9,
            "relation": "EQUAL_TO"
        }
        ],
        "isBlank": true,
        "formTemplateId": "da36f2c0-2c14-4076-85aa-63990ad9a037",
        "mcOptions": [],
        "questionIndex": 11,
        "questionId": "patient-final-diagnosis"
    }
    ]
}
"""

const val FORM_SELECTION_TEMP_FORM_SHORT =
"""
   {
    "version": "V1",
    "name": "NEMS Ambulance Request - sys test3",
    "dateCreated": 1655434930,
    "category": "Hopsital Report - sys test",
    "id": "ft6",
    "lastEdited": 1655434930,
    "lang": "english",
    "questions": [
        {
            "id": "5ab70a2f-6d7c-4b1f-86ce-da3c4c62f72d",
            "visibleCondition": [
                {
                    "qidx": 0,
                    "relation": "EQUAL_TO",
                    "answers": {
                        "number": 4
                    }
                }
            ],
            "isBlank": true,
            "formTemplateId": "ft6",
            "mcOptions": [
                {
                    "mcid": 0,
                    "opt": "male"
                },
                {
                    "mcid": 1,
                    "opt": "female"
                }
            ],
            "questionIndex": 0,
            "numMin": 10.01,
            "questionId": "referred-by-name",
            "questionText": "what's your sex?",
            "questionType": "MULTIPLE_CHOICE",
            "answers": {},
            "hasCommentAttached": false,
            "required": true
        }
    ]
}
"""
