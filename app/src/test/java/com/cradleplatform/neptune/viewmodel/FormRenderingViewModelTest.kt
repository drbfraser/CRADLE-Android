package com.cradleplatform.neptune.viewmodel

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.utilities.extensions.InstantExecutorExtension
import com.cradleplatform.neptune.viewmodel.forms.FormRenderingViewModel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito

@ExtendWith(InstantExecutorExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormRenderingViewModelTest {
    lateinit var viewModel: FormRenderingViewModel

    @BeforeAll
    fun initialize() {
        val networkStateManager = Mockito.mock(NetworkStateManager::class.java)
        val formResponseManager = Mockito.mock(FormResponseManager::class.java)
        val formManager = Mockito.mock(FormManager::class.java)
        val restApi = Mockito.mock(RestApi::class.java)
        viewModel = FormRenderingViewModel(
            networkStateManager,
            formResponseManager,
            formManager,
            restApi
        )
    }

    private fun initForm(questions: List<Question>, language: String = "English") {
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.form_uncategorized))
            .thenReturn("Uncategorized")
        Mockito.`when`(context.getString(R.string.form_generic_id))
            .thenReturn("Generic id %d")
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.initializeForm(formTemplate, null, "testPatient", language, null, false, context)
    }

    @Test
    fun `fullQuestionsList size 2`() {
        val questions = listOf(question(false, id = "id1"), question(false))
        initForm(questions)

        Assertions.assertEquals(viewModel.fullQuestionList().size, 2)
    }

    @Test
    fun `fullQuestionsList empty`() {
        initForm(listOf())
        assert(viewModel.fullQuestionList().isEmpty())
    }

    @Test
    fun `isRequiredFieldsFilled, no required fields`() {
        val context = Mockito.mock(Context::class.java)

        val questions = listOf(question(false, id = "id1"), question(false), question(false))
        initForm(questions)

        val (allFieldsFilledCorrectly, toastText) = viewModel.areAllFieldsFilledCorrectly(context)

        assert(allFieldsFilledCorrectly)
        Assertions.assertNull(toastText)
    }

    @Test
    fun `isAllFieldsFilledCorrectly, field exceeds line limit`() {
        val context = Mockito.mock(Context::class.java)

        val questions = listOf(
            question(false, "id1", 1, QuestionTypeEnum.STRING),
            question(false),
            question(false)
        )
        initForm(questions)
        viewModel.addAnswer(questions[0].id!!, Answer.createTextAnswer("\n \n \n"))

        Mockito.`when`(context.getString(R.string.form_generic_field_exceeds_line_limit))
            .thenReturn("Some fields exceed line limit")

        val (allFieldsFilledCorrectly, toastText) = viewModel.areAllFieldsFilledCorrectly(context)

        assert(!allFieldsFilledCorrectly)
        Assertions.assertEquals("Some fields exceed line limit", toastText)
    }

    @Test
    fun `populateEmptyIds, none empty`() {
        val questions = listOf(question(true, id = "id1"), question(true, id = "id2"))
        initForm(questions)

        viewModel.uiState.value.formTemplate?.questions?.forEach {
            assert(it.id?.isNotEmpty() == true)
        }
    }

    @Test
    fun `populateEmptyIds, one id null`() {
        val questions = listOf(question(true, id = ""), question(true, id = "id2"))
        initForm(questions)

        viewModel.uiState.value.formTemplate?.questions?.forEach {
            assert(it.id?.isNotEmpty() == true)
        }
    }

    private fun question(
        required: Boolean,
        id: String? = "generic_id",
        stringMaxLines: Int? = null,
        questionTypeEnum: QuestionTypeEnum = QuestionTypeEnum.INTEGER,
        allowPastDates: Boolean = true,
        allowFutureDates: Boolean = true
    ): Question {
        return Question(
            id = id,
            visibleCondition = null,
            isBlank = false,
            formTemplateId = null,
            questionIndex = 1,
            numMin = null,
            numMax = null,
            stringMaxLength = null,
            stringMaxLines = stringMaxLines,
            questionType = questionTypeEnum,
            hasCommentAttached = false,
            required = required,
            languageVersions = null,
            allowPastDates = allowPastDates,
            allowFutureDates = allowFutureDates
        )
    }
}
