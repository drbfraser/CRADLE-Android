package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
    private val _currentCategory: MutableLiveData<Int> = MutableLiveData(1)
    var categoryList: List<Pair<String, List<Question>?>>? = null

    fun currentCategory(): LiveData<Int> {
        return _currentCategory
    }

    fun changeCategory(currCategory: Int) {
        _currentCategory.value = currCategory
    }

    fun populateEmptyIds(context: Context) {
        currentFormTemplate?.questions?.forEachIndexed { index, Q ->
            if (Q.questionId?.isEmpty() == true && Q.questionType != QuestionTypeEnum.CATEGORY) {
                Q.questionId = String.format(context.getString(R.string.form_generic_id), index)
            }
        }
    }

    fun setCategorizedQuestions(languageSelected: String) {
        categoryList = getCategorizedQuestions(languageSelected)
    }

    private fun getCategorizedQuestions(languageSelected: String): List<Pair<String, List<Question>?>> {
        if (currentFormTemplate?.questions.isNullOrEmpty()) {
            return listOf()
        }

        val indicesOfCategory = mutableListOf<Int>()
        currentFormTemplate?.questions?.forEachIndexed { index, question ->
            if (question.questionType == QuestionTypeEnum.CATEGORY) {
                indicesOfCategory.add(index)
            }
        }

        var categoryName = R.string.form_uncategorized.toString()
        if (indicesOfCategory.isEmpty()) {
            return listOf(Pair(categoryName, currentFormTemplate!!.questions))
        }

        val categoryList = mutableListOf<Pair<String, List<Question>?>>()

        if (indicesOfCategory[0] != 0) {
            // There is uncategorized questions at start
            val sublist = currentFormTemplate!!.questions?.subList(0, indicesOfCategory[0])
            val uncategorizedPair = Pair(categoryName, sublist)
            categoryList.add(uncategorizedPair)
            indicesOfCategory.removeAt(0)
        }
        indicesOfCategory.forEachIndexed { i, categoryIndex ->
            val langVersion = currentFormTemplate!!.questions!![categoryIndex].languageVersions
            categoryName = langVersion?.find {
                it.language == languageSelected
            }?.questionText ?: R.string.not_available.toString()

            if (categoryIndex != currentFormTemplate!!.questions!!.size - 1) {
                //otherwise do not create a pair for this as category is last
                val indexOfNextCategory = if (i == indicesOfCategory.size - 1) {
                    // it is last category therefore sublist to end
                    currentFormTemplate!!.questions!!.size
                } else {
                    indicesOfCategory[i + 1]
                }
                val sublist = currentFormTemplate!!.questions?.subList(categoryIndex + 1, indexOfNextCategory)
                val categoryPair = Pair(categoryName, sublist)

                if (!sublist.isNullOrEmpty()) {
                    categoryList.add(categoryPair)
                }
            }
        }

        return categoryList
    }

    fun fullQuestionList(): MutableList<Question> {
        var listOfQuestions: MutableList<Question> = mutableListOf()
        currentFormTemplate?.questions?.forEach() { Q ->
            listOfQuestions.add(Q)
        }
        return listOfQuestions
    }

    fun getCurrentQuestionsList(): List<Question> {
        if (currentCategory().value == null) {
            return listOf()
        }
        return categoryList?.getOrNull(currentCategory().value!! - 1)?.second ?: listOf()
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
                    Protocol.valueOf(submissionMode),
                    applicationContext
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

    fun getRequiredFieldsText(questions: List<Question>?): String {
        var total = 0
        questions?.forEach {
            if (it.required == true) {
                //TODO check if required field is filled here.
                total++
            }
        }
        return "Required 0/$total"
    }

    fun getOptionalFieldsText(questions: List<Question>?): String {
        var total = 0
        questions?.forEach {
            if (it.required != true) {
                total++
            }
        }
        return "Optional 0/$total"
    }

    private companion object {
        private val currentAnswers = mutableMapOf<String, Answer>()
    }
}
