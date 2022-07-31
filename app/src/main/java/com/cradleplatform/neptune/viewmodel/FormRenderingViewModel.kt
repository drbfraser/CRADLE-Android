package com.cradleplatform.neptune.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.model.Answers
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
            Log.i("MVVM", "exist already!")
            return
        }
        for (i in form) {
            if (i.first == answer.first) {
                form[form.indexOf(i)] = answer
                Log.i("MVVM", "question $i has been rewrite")
                return
            }
        }
        form.add(answer)
        Log.i("MVVM", "Before sorted: $form")
        form.sortBy { it.first }
    }

    fun generateForm(template: FormTemplate) {
        myFormResult = template
        for (answer in form) {
            val theAnswer = Answers(answer.second)
            myFormResult!!.questions[answer.first].answers = theAnswer
        }
    }

    fun submitForm() {
        Log.i("MVVM", "Form needs to be submitted: $myFormResult")
    }
}