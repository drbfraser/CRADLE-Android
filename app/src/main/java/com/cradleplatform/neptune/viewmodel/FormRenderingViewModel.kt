package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.model.Answers
import com.cradleplatform.neptune.model.DtoData
import com.cradleplatform.neptune.model.FormTemplate

class FormRenderingViewModel : ViewModel() {

    var form = mutableListOf<Pair<Int, String>>()
    var currentCategory: Int = 1
    var myFormResult: FormTemplate? = null

    val currentAnswer: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    fun addAnswer(answer: Pair<Int, String>) {
        if (form.contains(answer)) {
            return
        }
        for (i in form) {
            if (i.first == answer.first) {
                form[form.indexOf(i)] = answer
                return
            }
        }
        form.add(answer)
        form.sortBy { it.first }
    }

    fun generateForm(template: FormTemplate) {
        myFormResult = template
        for (answer in form) {
            val theAnswer = Answers(answer.second)
            myFormResult!!.questions!![answer.first].answers = theAnswer
        }
    }

    suspend fun submitForm(mFormManager: FormManager) {
        mFormManager.putFormTemplate(DtoData.resultForm)
    }
}
