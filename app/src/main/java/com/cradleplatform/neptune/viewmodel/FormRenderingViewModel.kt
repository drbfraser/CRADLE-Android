package com.cradleplatform.neptune.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.model.Answers
import com.cradleplatform.neptune.model.DtoData
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.net.MyHttpClient
import com.google.gson.Gson
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.RequestParams
import cz.msebera.android.httpclient.Header

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
            myFormResult!!.questions[answer.first].answers = theAnswer
        }
    }

    fun submitForm() {
        postForm()
    }

    private fun postForm() {
        var pForm = DtoData.resultForm
        var param = RequestParams()
        var parajson = Gson().toJson(pForm)
        param.add("resultForm", parajson)
        MyHttpClient.post(
            //TODO: Change the url with web endpoint
            "http://baidu.com", param,
            object : AsyncHttpResponseHandler() {
                override fun onSuccess(p0: Int, p1: Array<out Header>?, p2: ByteArray?) {
                }

                override fun onFailure(
                    p0: Int,
                    p1: Array<out Header>?,
                    p2: ByteArray?,
                    p3: Throwable?
                ) {
                }
            }
        )
    }
}
