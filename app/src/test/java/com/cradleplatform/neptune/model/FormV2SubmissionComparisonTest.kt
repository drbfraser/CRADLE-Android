package com.cradleplatform.neptune.model

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FormV2SubmissionComparisonTest {
    @Test
    fun `real JSON numbers compare by value`() {
        val saved = Gson().fromJson(
            """{"id":"submission-1","formTemplateId":"template-1","patientId":"patient-1",
                "lang":"English","archived":false,
                "answers":[{"questionId":"q1","answer":{"number":5.0}}]}""",
            FormSubmissionWithAnswersV2::class.java
        )
        assertTrue(FormV2SubmissionComparison.matches(request(AnswerV2.createNumericAnswer(5L)), saved))
        assertTrue(FormV2SubmissionComparison.matches(request(AnswerV2.createNumericAnswer(5.0)), saved))
        assertFalse(FormV2SubmissionComparison.matches(request(AnswerV2.createNumericAnswer(6)), saved))
    }

    @Test
    fun `answer order and generated IDs do not change the answer values`() {
        val text = FormAnswerV2(questionId = "q1", answer = AnswerV2.createTextAnswer("Headache"))
        val choices = FormAnswerV2(questionId = "q2", answer = AnswerV2.createMcAnswer(listOf(0, 2)))
        val request = request(text.answer).copy(answers = listOf(text, choices))
        val saved = saved(text.answer).copy(answers = listOf(
            answer("q2", AnswerV2.createMcAnswer(listOf(2, 0))), answer("q1", text.answer)
        ))
        assertTrue(FormV2SubmissionComparison.matches(request, saved))
        assertFalse(FormV2SubmissionComparison.matches(request.copy(answers = listOf(text)), saved))
        assertFalse(FormV2SubmissionComparison.matches(request, saved.copy(answers = saved.answers?.take(1))))
    }

    @Test
    fun `different identity language archived or malformed answers cannot confirm success`() {
        val value = AnswerV2.createTextAnswer("Headache")
        val saved = saved(value)
        listOf(
            saved.copy(id = "other"), saved.copy(patientId = "other"),
            saved.copy(formTemplateId = "other"), saved.copy(lang = "French"),
            saved.copy(archived = true), saved.copy(answers = null),
            saved.copy(answers = saved.answers.orEmpty() + saved.answers.orEmpty())
        ).forEach { assertFalse(FormV2SubmissionComparison.matches(request(value), it)) }
        val duplicateRequest = request(value).copy(answers = request(value).answers + request(value).answers)
        assertFalse(FormV2SubmissionComparison.matches(duplicateRequest, saved))
    }

    @Test
    fun `text comment date and selected option changes remain different`() {
        listOf(
            AnswerV2.createTextAnswer("Headache") to AnswerV2.createTextAnswer("Fever"),
            AnswerV2.createTextAnswer("Headache", "mild") to AnswerV2.createTextAnswer("Headache", "severe"),
            AnswerV2.createDateAnswer("1700000000") to AnswerV2.createDateAnswer("1700000001"),
            AnswerV2.createMcAnswer(listOf(0)) to AnswerV2.createMcAnswer(listOf(1))
        ).forEach { (local, server) ->
            assertFalse(FormV2SubmissionComparison.matches(request(local), saved(server)))
        }
    }

    private fun request(value: AnswerV2) = CreateFormSubmissionRequestV2(
        id = "submission-1", patientId = "patient-1", formTemplateId = "template-1",
        answers = listOf(FormAnswerV2(questionId = "q1", answer = value))
    )

    private fun saved(value: AnswerV2) = FormSubmissionWithAnswersV2(
        id = "submission-1", patientId = "patient-1", formTemplateId = "template-1", userId = 1,
        lang = "English", archived = false, dateSubmitted = 0, lastEdited = 0,
        answers = listOf(answer("q1", value))
    )

    private fun answer(questionId: String, value: AnswerV2) = AnswerWithQuestionV2(
        id = "server-$questionId", formSubmissionId = "submission-1", questionId = questionId,
        answer = value, questionType = QuestionTypeEnum.STRING, questionText = "Question",
        mcOptions = emptyList(), order = 0
    )
}
