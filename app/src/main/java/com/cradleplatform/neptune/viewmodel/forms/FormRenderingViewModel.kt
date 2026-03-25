package com.cradleplatform.neptune.viewmodel.forms

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormResponse
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.connectivity.api24.ConnectivityOptions
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FormRenderingViewModel @Inject constructor(
    private val networkStateManager: NetworkStateManager,
    private val formResponseManager: FormResponseManager,
    private val formManager: FormManager,
    private val restApi: RestApi
) : ViewModel() {

    @Inject
    lateinit var smsDataProcessor: SMSDataProcessor

    @Inject
    lateinit var smsStateReporter: SmsStateReporter

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val _uiState = MutableStateFlow(FormState())
    val uiState: StateFlow<FormState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<FormSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    fun initializeForm(
        formTemplate: FormTemplate,
        patient: Patient?,
        patientId: String,
        language: String,
        formResponseId: Long?,
        isFromSavedResponse: Boolean,
        context: Context
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                formTemplate = formTemplate,
                patient = patient,
                patientId = patientId,
                language = language,
                formResponseId = formResponseId,
                isFromSavedResponse = isFromSavedResponse
            )
        }
        populateEmptyIds(context)
        setCategorizedQuestions(language, context)
    }

    fun changeCategory(currCategory: Int) {
        _uiState.update { it.copy(currentCategory = currCategory) }
    }

    fun toggleBottomSheet() {
        _uiState.update { it.copy(bottomSheetExpanded = !it.bottomSheetExpanded) }
    }

    fun hideBottomSheet() {
        _uiState.update { it.copy(bottomSheetExpanded = false) }
    }

    private fun populateEmptyIds(context: Context) {
        _uiState.value.formTemplate?.questions?.forEachIndexed { index, Q ->
            if (Q.id?.isEmpty() == true && Q.questionType != QuestionTypeEnum.CATEGORY) {
                Q.id = String.format(context.getString(R.string.form_generic_id), index)
            }
        }
    }

    private fun setCategorizedQuestions(languageSelected: String, context: Context) {
        Log.d("FormTemplateDebug", "Questions: ${_uiState.value.formTemplate?.questions}")
        val categories = getCategorizedQuestions(languageSelected, context)
        _uiState.update { it.copy(categoryList = categories) }
    }

    private fun getCategorizedQuestions(
        languageSelected: String,
        context: Context
    ): List<Pair<String, List<Question>?>> {
        val formTemplate = _uiState.value.formTemplate
        if (formTemplate?.questions.isNullOrEmpty()) {
            return listOf()
        }

        val indicesOfCategory = mutableListOf<Int>()
        formTemplate?.questions?.forEachIndexed { index, question ->
            if (question.questionType == QuestionTypeEnum.CATEGORY) {
                indicesOfCategory.add(index)
            }
        }

        var categoryName = context.getString(R.string.form_uncategorized)
        if (indicesOfCategory.isEmpty()) {
            return listOf(Pair(categoryName, formTemplate!!.questions))
        }

        val categoryList = mutableListOf<Pair<String, List<Question>?>>()

        if (indicesOfCategory[0] != 0) {
            // There is uncategorized questions at start
            val sublist = formTemplate!!.questions?.subList(0, indicesOfCategory[0])
            val uncategorizedPair = Pair(categoryName, sublist)
            categoryList.add(uncategorizedPair)
        }
        indicesOfCategory.forEachIndexed { i, categoryIndex ->
            val langVersion = formTemplate!!.questions!![categoryIndex].languageVersions
            categoryName = langVersion?.find {
                it.language == languageSelected
            }?.questionText ?: context.getString(R.string.not_available)

            if (categoryIndex != formTemplate.questions!!.size - 1) {
                //otherwise do not create a pair for this as category is last
                val indexOfNextCategory = if (i == indicesOfCategory.size - 1) {
                    // it is last category therefore sublist to end
                    formTemplate.questions!!.size
                } else {
                    indicesOfCategory[i + 1]
                }
                val sublist =
                    formTemplate.questions?.subList(categoryIndex + 1, indexOfNextCategory)
                val categoryPair = Pair(categoryName, sublist)

                if (!sublist.isNullOrEmpty()) {
                    categoryList.add(categoryPair)
                }
            }
        }

        return categoryList
    }

    fun fullQuestionList(): List<Question> {
        return _uiState.value.formTemplate?.questions ?: emptyList()
    }

    fun getCurrentQuestionsList(): List<Question> {
        val currentCategory = _uiState.value.currentCategory
        return _uiState.value.categoryList?.getOrNull(currentCategory - 1)?.second ?: listOf()
    }

    fun addAnswer(questionId: String, answer: Answer) {
        _uiState.update { currentState ->
            val updatedAnswers = currentState.currentAnswers.toMutableMap()
            updatedAnswers[questionId] = answer
            currentState.copy(
                currentAnswers = updatedAnswers,
                hasUnsavedChanges = true
            )
        }
    }

    fun deleteAnswer(questionId: String) {
        _uiState.update { currentState ->
            val updatedAnswers = currentState.currentAnswers.toMutableMap()
            updatedAnswers.remove(questionId)
            currentState.copy(
                currentAnswers = updatedAnswers,
                hasUnsavedChanges = true
            )
        }
    }

    fun getTextAnswer(questionId: String?): String? {
        if (questionId == null) return null
        return _uiState.value.currentAnswers[questionId]?.textAnswer
    }

    fun getNumericAnswer(questionId: String?): Number? {
        if (questionId == null) return null
        return _uiState.value.currentAnswers[questionId]?.numericAnswer
    }

    fun getMCAnswer(questionId: String?): List<Int>? {
        if (questionId == null) return null
        return _uiState.value.currentAnswers[questionId]?.mcIdArrayAnswer
    }

    fun clearAnswers() {
        _uiState.update { it.copy(currentAnswers = emptyMap(), hasUnsavedChanges = false) }
    }

    fun getInternetTypeString(): String {
        return when (networkStateManager.getConnectivity()) {
            ConnectivityOptions.WIFI -> "Wifi"
            ConnectivityOptions.CELLULAR_DATA -> "Cellular"
            ConnectivityOptions.SMS -> "Sms"
            ConnectivityOptions.NONE -> ""
        }
    }

    private fun addBlankQuestions(formTemplate: FormTemplate): Map<String, Answer> {
        val answers = _uiState.value.currentAnswers.toMutableMap()
        for (i in 1 until formTemplate.questions!!.size) {
            if (!answers.containsKey(formTemplate.questions[i].id.toString())) {
                answers[formTemplate.questions[i].id.toString()] = Answer.createEmptyAnswer()
            }
        }
        return answers
    }

    suspend fun submitForm(protocol: Protocol): NetworkResult<Unit> {
        val currentState = _uiState.value
        val formTemplate = currentState.formTemplate
        val patientId = currentState.patientId
        val selectedLanguage = currentState.language

        return if (formTemplate != null && patientId != null && selectedLanguage != null) {
            _uiState.update { it.copy(isLoading = true) }

            val answersWithBlanks = addBlankQuestions(formTemplate)
            val formResponse = FormResponse(
                patientId = patientId,
                formTemplate = formTemplate,
                language = selectedLanguage,
                answers = answersWithBlanks
            )

            val result = restApi.postFormResponse(formResponse, protocol)

            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "Form uploaded successfully.")
                    _sideEffects.send(FormSideEffect.ShowToast("Form submitted successfully"))
                    _sideEffects.send(FormSideEffect.FormSubmittedSuccessfully)
                    _uiState.update { it.copy(hasUnsavedChanges = false, isLoading = false) }
                }

                is NetworkResult.Failure -> {
                    Log.d(TAG, "Form upload failed with status ${result.statusCode}.")
                    val errorType = if (result.statusCode == 401 || result.statusCode == 403) {
                        SubmissionErrorType.AUTH_ERROR
                    } else {
                        SubmissionErrorType.SERVER_ERROR
                    }
                    _sideEffects.send(FormSideEffect.ShowSubmissionError(errorType))
                    _uiState.update { it.copy(isLoading = false) }
                }

                is NetworkResult.NetworkException -> {
                    Log.d(TAG, "Form upload failed: ${result.cause.message}")
                    _sideEffects.send(FormSideEffect.ShowSubmissionError(SubmissionErrorType.NETWORK_ERROR))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }

            result
        } else {
            _uiState.update { it.copy(isLoading = false) }
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    /**
     * Returns true if conditions of all field inputs are validated successfully
     */
    fun areAllFieldsFilledCorrectly(context: Context): Pair<Boolean, String?> {
        val languageSelected = _uiState.value.language ?: return Pair(false, "Language not selected")
        val currentAnswers = _uiState.value.currentAnswers

        fullQuestionList().forEach {
            val answer = currentAnswers[it.id]

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
     * Returns validation status for required fields in a category
     */
    data class FieldsStatus(
        val text: String,
        val isComplete: Boolean,
        val hasErrors: Boolean
    )

    fun getRequiredFieldsStatus(questions: List<Question>?): FieldsStatus {
        var total = 0
        var totalAnswered = 0
        var hasErrors = false
        val currentAnswers = _uiState.value.currentAnswers

        questions?.forEach {
            val currAnswer = currentAnswers[it.id]
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
        val isComplete = !hasErrors && totalAnswered == total
        return FieldsStatus("Required $totalAnswered/$total", isComplete, hasErrors)
    }

    fun getOptionalFieldsText(questions: List<Question>?): String {
        var total = 0
        var totalAnswered = 0
        val currentAnswers = _uiState.value.currentAnswers

        questions?.forEach {
            if (it.required != true) {
                if (currentAnswers[it.id] != null) {
                    totalAnswered++
                }
                total++
            }
        }
        return "Optional $totalAnswered/$total"
    }

    fun canGoToNextCategory(): Boolean {
        return _uiState.value.currentCategory != _uiState.value.categoryList?.size
    }

    fun canGoToPrevCategory(): Boolean {
        return _uiState.value.currentCategory != FIRST_CATEGORY_POSITION
    }

    fun goNextCategory() {
        if (canGoToNextCategory()) {
            changeCategory(_uiState.value.currentCategory + 1)
        }
    }

    fun goPrevCategory() {
        if (canGoToPrevCategory()) {
            changeCategory(_uiState.value.currentCategory - 1)
        }
    }

    suspend fun removeFormResponseFromDatabaseById(formResponseId: Long) =
        formResponseManager.deleteFormResponseById(formResponseId)

    suspend fun saveFormResponseToDatabase(saveDraft: Boolean) {
        val currentState = _uiState.value
        val formTemplate = currentState.formTemplate
        val patientId = currentState.patientId
        val selectedLanguage = currentState.language
        val formResponseId = currentState.formResponseId

        return if (formTemplate != null && patientId != null && selectedLanguage != null) {
            // If the FormTemplate's formClassName field is empty, grab it using formClass Id
            formTemplate.formClassName =
                formTemplate.formClassName ?: formTemplate.formClassId?.let {
                    formManager.searchForFormClassNameWithFormClassId(it)
                }

            val formResponse =
                when (formResponseId != null && formResponseManager.searchForFormResponseById(
                    formResponseId
                ) != null) {
                    true -> FormResponse(
                        formResponseId = formResponseId,
                        patientId = patientId,
                        formTemplate = formTemplate,
                        language = selectedLanguage,
                        answers = currentState.currentAnswers,
                        saveResponseToSendLater = saveDraft
                    )

                    false -> FormResponse(
                        patientId = patientId,
                        formTemplate = formTemplate,
                        language = selectedLanguage,
                        answers = currentState.currentAnswers,
                        saveResponseToSendLater = saveDraft
                    )
                }
            formResponseManager.updateOrInsertIfNotExistsFormResponse(formResponse)
            _uiState.update { it.copy(hasUnsavedChanges = false) }
            _sideEffects.send(FormSideEffect.ShowToast("Form saved successfully"))
        } else {
            error("FormTemplate does not exist: Current displaying FormTemplate is null")
        }
    }

    companion object {
        private const val TAG = "FormRenderingViewModel"
        const val FIRST_CATEGORY_POSITION = 1
    }
}

enum class InvalidInputTypeEnum {
    REQUIRED_FIELD,
    STRING_MAX_LINES
}
