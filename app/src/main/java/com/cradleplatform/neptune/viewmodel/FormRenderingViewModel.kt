package com.cradleplatform.neptune.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
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

    fun fullQuestionList(): MutableList<Question> {
        var listOfQuestions: MutableList<Question> = mutableListOf()
        currentFormTemplate?.questions?.forEach() { Q ->
            listOfQuestions.add(Q)
        }
        return listOfQuestions
    }

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
                    formTemplate = currentFormTemplate!!,
                    language = selectedLanguage,
                    answers = currentAnswers
                )
            )
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }
    private companion object {
        //Current user answer
        private const val TAG = "FormRenderingViewModel"
        private val currentAnswers = mutableMapOf<String, Answer>()
    }
}
