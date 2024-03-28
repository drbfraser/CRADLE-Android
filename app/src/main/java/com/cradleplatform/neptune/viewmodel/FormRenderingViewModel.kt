package com.cradleplatform.neptune.viewmodel

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat.getDrawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.DatabaseObject
import com.cradleplatform.neptune.http_sms_service.http.HttpSmsService
import com.cradleplatform.neptune.http_sms_service.http.Protocol
import com.cradleplatform.neptune.http_sms_service.sms.SMSReceiver
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.utilities.connectivity.api24.ConnectivityOptions
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.view.FormRenderingActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FormRenderingViewModel @Inject constructor(
    private val httpSmsService: HttpSmsService,
    private val networkStateManager: NetworkStateManager,
    private val smsSender: SMSSender,
    private val formResponseManager: FormResponseManager,
    private val formManager: FormManager
) : ViewModel() {

    //Raw form template
    var currentFormTemplate: FormTemplate? = null
    private val _currentCategory: MutableLiveData<Int> = MutableLiveData(1)
    private val _currentAnswers: MutableLiveData<Map<String, Answer>?> =
        MutableLiveData(mutableMapOf())

    @Inject
    lateinit var smsDataProcessor: SMSDataProcessor

    @Inject
    lateinit var smsStateReporter: SmsStateReporter

    @Inject
    lateinit var sharedPreferences: SharedPreferences
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

    private fun getCategorizedQuestions(
        languageSelected: String,
        context: Context
    ): List<Pair<String, List<Question>?>> {
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
                val sublist =
                    currentFormTemplate!!.questions?.subList(categoryIndex + 1, indexOfNextCategory)
                val categoryPair = Pair(categoryName, sublist)

                if (!sublist.isNullOrEmpty()) {
                    categoryList.add(categoryPair)
                }
            }
        }

        return categoryList
    }

    fun fullQuestionList(): MutableList<Question> {
        val listOfQuestions: MutableList<Question> = mutableListOf()
        currentFormTemplate?.questions?.forEach { Q ->
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
        return when (networkStateManager.getConnectivity()) {
            ConnectivityOptions.WIFI -> "Wifi"
            ConnectivityOptions.CELLULAR_DATA -> "Cellular"
            ConnectivityOptions.SMS -> "Sms"
            ConnectivityOptions.NONE -> ""
        }
    }

    fun getSMSReceiver(): SMSReceiver {
        val phoneNumber = sharedPreferences.getString(UserViewModel.RELAY_PHONE_NUMBER, null)
            ?: error("invalid phone number")
        return SMSReceiver(smsSender, phoneNumber, smsStateReporter)
    }

    fun addBlankQuestions(formTemplate: FormTemplate) {
        for (i in 1 until formTemplate.questions!!.size) {
            if (!currentAnswers.containsKey(formTemplate.questions[i].questionId.toString())) {
                currentAnswers[formTemplate.questions[i].questionId.toString()] =
                    Answer.createEmptyAnswer()
            }
        }
    }

    suspend fun submitForm(
        patientId: String,
        selectedLanguage: String,
        submissionMode: String,
        applicationContext: Context,
        formResponseId: Long?
    ) {
//        formResponseId?.let {
//            viewModelScope.launch {
//                removeFormResponseFromDatabaseById(it)
//            }
//        }

        return if (currentFormTemplate != null) {
            addBlankQuestions(currentFormTemplate!!)
            val formResponse = FormResponse(
                patientId = patientId,
                formTemplate = currentFormTemplate!!,
                language = selectedLanguage,
                answers = currentAnswers
            )
            httpSmsService.upload(
                DatabaseObject.FormResponseWrapper(
                    formResponse,
                    smsSender,
                    Protocol.valueOf(submissionMode),
                    applicationContext,
                    smsDataProcessor
                )
            )
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    /**
     * Returns true if conditions of all field inputs are validated successfully
     */
    fun areAllFieldsFilledCorrectly(
        languageSelected: String,
        context: Context
    ): Pair<Boolean, String?> {
        fullQuestionList().forEach {
            val answer = currentAnswers[it.questionId]

            /**
             * Checks of if all required fields are filled
             * Else returns false and shows user a toast of which field needs to be filled in
             */
            if (it.required == true) {
                if (answer?.isValidAnswer() != true) {
                    val toastText = createInvalidInputToast(
                        InvalidInputTypeEnum.REQUIRED_FIELD,
                        it,
                        languageSelected,
                        context
                    )
                    return Pair(false, toastText)
                }
            }

            /**
             * Checks if all required fields meet stringMaxlines restriction
             * Else returns false and shows user a toast of which field does not meet restriction
             */
            if (it.questionType == QuestionTypeEnum.STRING && it.stringMaxLines != null) {
                val lines = answer?.textAnswer?.lines()?.size
                if (lines != null && lines > it.stringMaxLines) {
                    val toastText = createInvalidInputToast(
                        InvalidInputTypeEnum.STRING_MAX_LINES,
                        it,
                        languageSelected,
                        context
                    )
                    return Pair(false, toastText)
                }
            }
        }
        return Pair(true, null)
    }

    private fun createInvalidInputToast(
        errorType: InvalidInputTypeEnum,
        question: Question,
        languageSelected: String,
        context: Context
    ): String {
        val questionText = question.languageVersions?.find { lang ->
            lang.language == languageSelected
        }?.questionText
        val toastText = when (errorType) {
            InvalidInputTypeEnum.REQUIRED_FIELD -> {
                if (questionText.isNullOrEmpty()) {
                    context.getString(R.string.form_generic_is_required)
                } else {
                    String.format(
                        context.getString(R.string.form_question_is_required),
                        questionText
                    )
                }
            }

            InvalidInputTypeEnum.STRING_MAX_LINES -> {
                if (questionText.isNullOrEmpty()) {
                    context.getString(R.string.form_generic_field_exceeds_line_limit)
                } else {
                    String.format(
                        context.getString(R.string.form_field_exceeds_line_limit),
                        questionText
                    )
                }
            }
        }
        return toastText
    }

    /**
     * Returns pair that contains required fields text and required fields icon
     */
    fun getRequiredFieldsTextAndIcon(
        questions: List<Question>?,
        context: Context
    ): Pair<String, Drawable?> {
        var total = 0
        var totalAnswered = 0
        var hasErrors = false
        questions?.forEach {
            val currAnswer = currentAnswers[it.questionId]
            if (it.required == true) {
                if (currAnswer != null) {
                    totalAnswered++
                }
                total++
            }
            if (it.questionType == QuestionTypeEnum.STRING && it.stringMaxLines != null) {
                val lines = currAnswer?.textAnswer?.split("\n")?.size
                if (lines != null && lines > it.stringMaxLines) {
                    hasErrors = true
                }
            }
        }
        var drawable = getDrawable(context, R.drawable.ic_baseline_warning_24)
        if (!hasErrors && totalAnswered == total) {
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

    fun setSMSSenderContext(activity: Activity) {
        smsSender.setActivityContext(activity)
    }

    suspend fun removeFormResponseFromDatabaseById(formResponseId: Long) =
        formResponseManager.deleteFormResponseById(formResponseId)

    suspend fun saveFormResponseToDatabase(
        patientId: String,
        selectedLanguage: String,
        formResponseId: Long?,
        saveDraft: Boolean
    ) {
        return if (currentFormTemplate != null) {
            // If the FormTemplate's formClassName field is empty, grab it using formClass Id
            currentFormTemplate?.formClassName =
                currentFormTemplate?.formClassName ?: currentFormTemplate?.formClassId?.let {
                    formManager.searchForFormClassNameWithFormClassId(
                        it
                    )
                }
            val formResponse =
                when (formResponseId != null && formResponseManager.searchForFormResponseById(
                    formResponseId
                ) != null) {
                    true -> FormResponse(
                        formResponseId = formResponseId,
                        patientId = patientId,
                        formTemplate = currentFormTemplate!!,
                        language = selectedLanguage,
                        answers = currentAnswers,
                        saveResponseToSendLater = saveDraft
                    )

                    false -> FormResponse(
                        patientId = patientId,
                        formTemplate = currentFormTemplate!!,
                        language = selectedLanguage,
                        answers = currentAnswers,
                        saveResponseToSendLater = saveDraft
                    )
                }
            formResponseManager.updateOrInsertIfNotExistsFormResponse(formResponse)
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    private companion object {
        private val currentAnswers = mutableMapOf<String, Answer>()
    }
}

enum class InvalidInputTypeEnum {
    REQUIRED_FIELD,
    STRING_MAX_LINES
}
