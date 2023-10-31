package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.http_sms_service.http.HttpSmsService
import com.cradleplatform.neptune.manager.SmsKeyManager
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionTypeEnum
import org.junit.jupiter.api.BeforeAll
import org.mockito.Mockito

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FormRenderingViewModelTest {
    lateinit var viewModel: FormRenderingViewModel

    @BeforeAll
    fun initialize() {
        val httpsSmsService = Mockito.mock(HttpSmsService::class.java)
        val smsKeyManager = Mockito.mock(SmsKeyManager::class.java)
        val sharedPreferences = Mockito.mock(SharedPreferences::class.java)
        viewModel = FormRenderingViewModel(httpsSmsService, sharedPreferences, smsKeyManager)
    }

    @Test
    fun `fullQuestionsList size 2`() {
        val questions = listOf(question(false, id = "id1"), question(false))
        val formTemplate = FormTemplate(null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        Assertions.assertEquals(viewModel.fullQuestionList().size, 2)
    }

    @Test
    fun `fullQuestionsList empty`() {
        val formTemplate = FormTemplate(null, null, null, null, null, listOf())
        viewModel.currentFormTemplate = formTemplate
        assert(viewModel.fullQuestionList().isEmpty())
    }

    @Test
    fun `isRequiredFieldsFilled, no required fields`() {
        val questions = listOf(question(false, id = "id1"), question(false), question(false))
        val formTemplate = FormTemplate(null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        val context = Mockito.mock(Context::class.java)

        Mockito.`when`(context.getString(R.string.form_question_is_required))
            .thenReturn("Please fill required field: %s")

        assert(viewModel.isRequiredFieldsFilled("English", context ))
    }

    @Test
    fun `populateEmptyIds, none empty`() {
        val questions = listOf(question(true, id = "id1"), question(true, id = "id2"))
        val formTemplate = FormTemplate(null, null, null, null, null, questions)
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
        val formTemplate = FormTemplate(null, null, null, null, null, questions)
        viewModel.currentFormTemplate = formTemplate

        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.getString(R.string.form_generic_id))
            .thenReturn("Generic id %d")
        viewModel.populateEmptyIds(context)

        viewModel.currentFormTemplate?.questions?.forEach {
            assert(it.questionId?.isNotEmpty() == true)
        }
    }

    private fun question(required: Boolean, id: String? = "generic_id"): Question {
        return Question(
                id = id,
                visibleCondition = null,
                isBlank = false,
                formTemplateId = null,
                questionIndex = 1,
                numMin = null,
                numMax = null,
                stringMaxLength = null,
                questionId = id,
                questionType = QuestionTypeEnum.INTEGER,
                hasCommentAttached = false,
                required = required,
                languageVersions = null
        )
    }

}