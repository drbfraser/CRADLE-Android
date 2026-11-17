package com.cradleplatform.neptune.database

import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.AnswerWithQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class FormSubmissionV2TypeConvertersTest {
    private val converters = FormSubmissionV2TypeConverters()

    @Test
    fun `downloaded answers retain question metadata and numeric values`() {
        val answer = AnswerWithQuestionV2(
            id = "answer-1", questionId = "question-1", formSubmissionId = "submission-1",
            answer = AnswerV2.createNumericAnswer(12.75, "Recorded value"),
            questionType = QuestionTypeEnum.DECIMAL, questionText = "Measurement",
            mcOptions = emptyList(), order = 2
        )
        val restored = converters.jsonToSubmissionAnswersV2(
            converters.submissionAnswersV2ToJson(listOf(answer))
        ).single()
        assertEquals(answer.copy(answer = restored.answer), restored)
        assertEquals(12.75, restored.answer.numericAnswer?.toDouble())
        assertEquals("Recorded value", restored.answer.comment)
    }

    @Test
    fun `empty answers are valid but missing answers are not silently accepted`() {
        assertEquals(emptyList<AnswerWithQuestionV2>(), converters.jsonToSubmissionAnswersV2("[]"))
        assertThrows(IllegalArgumentException::class.java) { converters.jsonToSubmissionAnswersV2("null") }
    }
}
