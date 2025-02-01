package com.cradleplatform.neptune.viewmodel

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
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
        val smsSender = Mockito.mock(SMSSender::class.java)
        val formResponseManager = Mockito.mock(FormResponseManager::class.java)
        val formManager = Mockito.mock(FormManager::class.java)
        val restApi = Mockito.mock(RestApi::class.java)
        viewModel = FormRenderingViewModel(
            networkStateManager,
            smsSender,
            formResponseManager,
            formManager,
            restApi
        )
    }

    @Test
    fun `fullQuestionsList size 2`() {
        val questions = listOf(question(false, id = "id1"), question(false))
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        Assertions.assertEquals(viewModel.fullQuestionList().size, 2)
    }

    @Test
    fun `fullQuestionsList empty`() {
        val formTemplate = FormTemplate(null, null, null, null, null, null, listOf())
        viewModel.currentFormTemplate = formTemplate
        assert(viewModel.fullQuestionList().isEmpty())
    }

    @Test
    fun `isRequiredFieldsFilled, no required fields`() {
        // Arrange
        val context = Mockito.mock(Context::class.java)

        val questions = listOf(question(false, id = "id1"), question(false), question(false))
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        // Act
        val (allFieldsFilledCorrectly, toastText) = viewModel.areAllFieldsFilledCorrectly(
            "English",
            context
        )

        // Assert
        assert(allFieldsFilledCorrectly)
        Assertions.assertNull(toastText)
    }

    @Test
    fun `isAllFieldsFilledCorrectly, field exceeds line limit`() {
        // Arrange
        val context = Mockito.mock(Context::class.java)

        val questions = listOf(
            question(false, "id1", 1, QuestionTypeEnum.STRING),
            question(false),
            question(false)
        )
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate
        viewModel.addAnswer(questions[0].questionId!!, Answer.createTextAnswer("\n \n \n"))

        Mockito.`when`(context.getString(R.string.form_generic_field_exceeds_line_limit))
            .thenReturn("Some fields exceed line limit")

        // Act
        val (allFieldsFilledCorrectly, toastText) = viewModel.areAllFieldsFilledCorrectly(
            "English",
            context
        )

        // Assert
        assert(!allFieldsFilledCorrectly)
        Assertions.assertEquals("Some fields exceed line limit", toastText)
    }

    @Test
    fun `populateEmptyIds, none empty`() {
        val questions = listOf(question(true, id = "id1"), question(true, id = "id2"))
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        val context = Mockito.mock(Context::class.java)
        viewModel.populateEmptyIds(context)

        viewModel.currentFormTemplate?.questions?.forEach {
            assert(it.questionId?.isNotEmpty() == true)
        }
    }

    @Test
    fun `populateEmptyIds, one id null`() {
        val questions = listOf(question(true, id = ""), question(true, id = "id2"))
        val formTemplate = FormTemplate(null, null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.form_generic_id))
            .thenReturn("Generic id %d")
        viewModel.populateEmptyIds(context)

        viewModel.currentFormTemplate?.questions?.forEach {
            assert(it.questionId?.isNotEmpty() == true)
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
            questionId = id,
            questionType = questionTypeEnum,
            hasCommentAttached = false,
            required = required,
            languageVersions = null,
            allowPastDates = allowPastDates,
            allowFutureDates = allowFutureDates
        )
    }
}