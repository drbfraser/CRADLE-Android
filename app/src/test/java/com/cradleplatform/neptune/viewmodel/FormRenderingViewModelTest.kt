package com.cradleplatform.neptune.viewmodel

import android.content.SharedPreferences
import com.cradleplatform.neptune.http_sms_service.http.HttpSmsService
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
        val sharedPreferences = Mockito.mock(SharedPreferences::class.java)
        viewModel = FormRenderingViewModel(httpsSmsService, sharedPreferences)
    }

    @Test
    fun `fullQuestionsList size 2`() {
        val question = Question(
            id = null,
            visibleCondition = null,
            isBlank = false,
            formTemplateId = null,
            questionIndex = 1,
            numMin = null,
            numMax = null,
            stringMaxLength = null,
            questionId = "1",
            questionType = QuestionTypeEnum.INTEGER,
            hasCommentAttached = false,
            required = false,
            languageVersions = null
        )
        val formTemplate = Mockito.mock(FormTemplate::class.java)
        Mockito.`when`(formTemplate.questions)
            .thenReturn(listOf(question,question))

        viewModel.currentFormTemplate = formTemplate

        Assertions.assertEquals(viewModel.fullQuestionList().size, 2)
    }


}