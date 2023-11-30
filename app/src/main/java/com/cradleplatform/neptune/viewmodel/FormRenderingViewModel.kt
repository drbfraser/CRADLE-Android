package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.core.content.ContextCompat.getDrawable
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
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.encodeMsg
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.formatSMS
import com.cradleplatform.neptune.utilities.SMSFormatter.Companion.listToString
import com.cradleplatform.neptune.utilities.connectivity.api24.ConnectivityOptions
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.view.FormRenderingActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FormRenderingViewModel @Inject constructor(
    //private val mFormManager: FormManager,
    private val httpSmsService: HttpSmsService,
    private val sharedPreferences: SharedPreferences,
    private val networkStateManager: NetworkStateManager,
    private var smsKeyManager: SmsKeyManager,
    private val smsSender: SMSSender,
) : ViewModel() {

    //Raw form template
    var currentFormTemplate: FormTemplate? = null
    private val _currentCategory: MutableLiveData<Int> = MutableLiveData(1)
    private val _currentAnswers: MutableLiveData<Map<String, Answer>?> = MutableLiveData(mutableMapOf())

    var categoryList: List<Pair<String, List<Question>?>>? = null

    fun currentCategory(): LiveData<Int> {
        return _currentCategory
    }

    fun currentAnswers(): LiveData<Map<String, Answer>?> {
        return _currentAnswers
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

    fun setCategorizedQuestions(languageSelected: String, context: Context) {
        categoryList = getCategorizedQuestions(languageSelected, context)
    }

    private fun getCategorizedQuestions(languageSelected: String, context: Context):
        List<Pair<String, List<Question>?>> {
        if (currentFormTemplate?.questions.isNullOrEmpty()) {
            return listOf()
        }

        val indicesOfCategory = mutableListOf<Int>()
        currentFormTemplate?.questions?.forEachIndexed { index, question ->
            if (question.questionType == QuestionTypeEnum.CATEGORY) {
                indicesOfCategory.add(index)
            }
        }

        var categoryName = context.getString(R.string.form_uncategorized)
        if (indicesOfCategory.isEmpty()) {
            return listOf(Pair(categoryName, currentFormTemplate!!.questions))
        }

        val categoryList = mutableListOf<Pair<String, List<Question>?>>()

        if (indicesOfCategory[0] != 0) {
            // There is uncategorized questions at start
            val sublist = currentFormTemplate!!.questions?.subList(0, indicesOfCategory[0])
            val uncategorizedPair = Pair(categoryName, sublist)
            categoryList.add(uncategorizedPair)
        }
        indicesOfCategory.forEachIndexed { i, categoryIndex ->
            val langVersion = currentFormTemplate!!.questions!![categoryIndex].languageVersions
            categoryName = langVersion?.find {
                it.language == languageSelected
            }?.questionText ?: context.getString(R.string.not_available)

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
        _currentAnswers.value = currentAnswers
    }

    fun deleteAnswer(questionId: String) {
        currentAnswers.remove(questionId)
        _currentAnswers.value = currentAnswers
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

    fun getInternetTypeString(context: Context): String {
        when (networkStateManager.getConnectivity()) {
            ConnectivityOptions.WIFI -> return "Wifi"
            ConnectivityOptions.CELLULAR_DATA -> return "Cellular"
            ConnectivityOptions.SMS -> return "Sms"
            ConnectivityOptions.NONE -> return ""
        }
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

            // Retrieve the encrypted secret key
            val smsSecretKey = smsKeyManager.retrieveSmsKey()
                ?: // TODO: handle the case when the secret key is not available
                error("Encryption failed - no valid smsSecretKey is available")
            val encodedMsg = encodeMsg(json, smsSecretKey)

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

    /**
     * Returns pair that contains required fields text and required fields icon
     */
    fun getRequiredFieldsTextAndIcon(questions: List<Question>?, context: Context): Pair<String, Drawable?> {
        var total = 0
        var totalAnswered = 0
        questions?.forEach {
            if (it.required == true) {
                if (currentAnswers[it.questionId] != null) {
                    totalAnswered++
                }
                total++
            }
        }
        var drawable = getDrawable(context, R.drawable.ic_baseline_warning_24)
        if (totalAnswered == total) {
            drawable = getDrawable(context, R.drawable.ic_baseline_check_circle_24)
        }
        return Pair("Required $totalAnswered/$total", drawable)
    }

    fun getOptionalFieldsText(questions: List<Question>?): String {
        var total = 0
        var totalAnswered = 0
        questions?.forEach {
            if (it.required != true) {
                if (currentAnswers[it.questionId] != null) {
                    totalAnswered++
                }
                total++
            }
        }
        return "Optional $totalAnswered/$total"
    }

    fun isNextButtonVisible(context: Context): Drawable? {
        if (currentCategory().value == categoryList?.size) {
            return getDrawable(context, R.drawable.ic_arrow_forward_grey_24)
        }
        return getDrawable(context, R.drawable.ic_arrow_forward_black_24)
    }

    fun isPrevButtonVisible(context: Context): Drawable? {
        if (currentCategory().value == FormRenderingActivity.FIRST_CATEGORY_POSITION) {
            return getDrawable(context, R.drawable.ic_arrow_prev_grey_24)
        }
        return getDrawable(context, R.drawable.ic_arrow_prev_black_24)
    }

    fun goNextCategory() {
        if (currentCategory().value != categoryList?.size) {
            changeCategory(_currentCategory.value?.plus(1) ?: 1)
        }
    }

    fun goPrevCategory() {
        if (currentCategory().value != 1) {
            changeCategory(_currentCategory.value?.minus(1) ?: 1)
        }
    }

    private companion object {
        private val currentAnswers = mutableMapOf<String, Answer>()
    }
}
