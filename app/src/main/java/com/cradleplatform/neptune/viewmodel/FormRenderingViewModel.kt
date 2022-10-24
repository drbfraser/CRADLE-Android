package com.cradleplatform.neptune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.net.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.jvm.Throws

@HiltViewModel
class FormRenderingViewModel @Inject constructor(
    private val mFormManager: FormManager,
) : ViewModel() {

    //Raw form template
    var currentFormTemplate: FormTemplate? = null

    //private var currentCategory: Int = 1

    fun addAnswer(questionId: String, answer: Answer) {
        currentAnswers[questionId] = answer
        Log.d(TAG, "adding answer for [$questionId]")
    }

    @Throws(IllegalArgumentException::class, NullPointerException::class)
    suspend fun submitForm(patientId: String, selectedLanguage: String): NetworkResult<Unit> {
        return if (currentFormTemplate != null) {
            mFormManager.submitFormToWebAsResponse(
                FormResponse(
                    patientId = patientId,
                    formTemplate = dataTransferTemplate!!,
                    language = selectedLanguage,
                    answers = currentAnswers
                )
            )
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    /**
     * Set current FormTemplate to Render if not set
     * @return true if successfully updated current rendering form, false if failed
     */
    fun setRenderingFormIfNull(formTemplate: FormTemplate): Boolean {
        return if (dataTransferTemplate == null) {
            dataTransferTemplate = formTemplate
            true
        } else {
            false
        }
    }

    fun resetRenderingFormAndAnswers() {
        currentAnswers.clear()
        dataTransferTemplate = null
    }

    /**
     * Moved what DtoData is doing here;
     * 1) to save a copy of the original form tempalate
     * 2) to save the answered responses
     *
     * This "saving" is only required since there is multiple instance of the rendering activity
     * Recommend to move away from this hacky solution as soon as possible as its'
     * a) saves states that persists between ViewModel creation and destruction
     *      thus would be detrimental to the testability of code
     */
    private companion object {
        //Current user answer
        private const val TAG = "FormRenderingViewModel"
        private val currentAnswers = mutableMapOf<String, Answer>()
        private var dataTransferTemplate: FormTemplate? = null // data transfer object
    }
}
