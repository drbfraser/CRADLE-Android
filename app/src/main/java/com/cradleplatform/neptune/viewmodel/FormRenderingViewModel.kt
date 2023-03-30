package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.DatabaseObject
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.http_sms_service.http.HttpSmsService
import com.cradleplatform.neptune.http_sms_service.http.Protocol
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.utilities.AESEncryptor.Companion.getSecretKeyFromString
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.encodeMsg
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.formatSMS
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.listToString
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FormRenderingViewModel @Inject constructor(
    //private val mFormManager: FormManager,
    private val httpSmsService: HttpSmsService,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    //Raw form template
    var currentFormTemplate: FormTemplate? = null

    fun populateEmptyIds(context: Context) {
        currentFormTemplate?.questions?.forEachIndexed { index, Q ->
            if (Q.questionId?.isEmpty() == true && Q.questionType != QuestionTypeEnum.CATEGORY) {
                Q.questionId = String.format(context.getString(R.string.form_generic_id), index)
            }
        }
    }

    fun fullQuestionList(): MutableList<Question> {
        var listOfQuestions: MutableList<Question> = mutableListOf()
        currentFormTemplate?.questions?.forEach() { Q ->
            listOfQuestions.add(Q)
        }
        return listOfQuestions
    }

    fun addAnswer(questionId: String, answer: Answer) {
        currentAnswers[questionId] = answer
    }

    fun deleteAnswer(questionId: String) {
        currentAnswers.remove(questionId)
    }

    fun getTextAnswer(questionId: String?): String? {
        if (questionId == null) return null
        return currentAnswers[questionId]?.textAnswer
    }

    fun getNumericAnswer(questionId: String?): Number? {
        if (questionId == null) return null
        return currentAnswers[questionId]?.numericAnswer
    }

    fun getMCAnswer(questionId: String?): List<Int>? {
        if (questionId == null) return null
        return currentAnswers[questionId]?.mcidArrayAnswer
    }

    fun clearAnswers() {
        currentAnswers.clear()
    }

    suspend fun submitForm(
        patientId: String,
        selectedLanguage: String,
        submissionMode: String,
        applicationContext: Context
    ) {
        return if (currentFormTemplate != null) {
            val formResponse = FormResponse(
                patientId = patientId,
                formTemplate = currentFormTemplate!!,
                language = selectedLanguage,
                answers = currentAnswers
            )

            val json = JacksonMapper.createWriter<FormResponse>().writeValueAsString(
                formResponse
            )

            val encodedMsg = encodeMsg(
                json,
                getSecretKeyFromString(applicationContext.getString(R.string.aes_secret_key))
            )

            val smsRelayRequestCounter = sharedPreferences.getLong(
                applicationContext.getString(R.string.sms_relay_request_counter), 0
            )

            val msgInPackets = listToString(
                formatSMS(encodedMsg, smsRelayRequestCounter)
            )

            /*This is something that needs to reworked, refer to issue #114*/
            sharedPreferences.edit(commit = true) {
                putString(applicationContext.getString(R.string.sms_relay_list_key), msgInPackets)
                putLong(
                    applicationContext.getString(R.string.sms_relay_request_counter),
                    smsRelayRequestCounter + 1
                )
            }

            val smsSender = SMSSender(sharedPreferences, applicationContext)

            httpSmsService.upload(
                DatabaseObject.FormResponseWrapper(
                    formResponse,
                    smsSender,
                    Protocol.valueOf(submissionMode)
                )
            )
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    /**
     * Returns true if all required fields filled
     * Else returns false and shows user a toast of which field needs to be filled in
     */
    fun isRequiredFieldsFilled(languageSelected: String, context: Context): Boolean {
        fullQuestionList().forEach {
            if (it.required == true) {
                val answer = currentAnswers[it.questionId]
                if (answer?.isValidAnswer() != true) {
                    createToast(it, languageSelected, context)
                    return false
                }
            }
        }
        return true
    }

    private fun createToast(question: Question, languageSelected: String, context: Context) {
        val questionText = question.languageVersions?.find { lang ->
            lang.language == languageSelected
        }?.questionText
        val toastText = if (questionText.isNullOrEmpty()) {
            context.getString(R.string.form_generic_is_required)
        } else {
            String.format(context.getString(R.string.form_question_is_required), questionText)
        }
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
    }

    private companion object {
        private val currentAnswers = mutableMapOf<String, Answer>()
    }
}
