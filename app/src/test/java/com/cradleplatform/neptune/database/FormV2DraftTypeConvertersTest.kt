package com.cradleplatform.neptune.database

import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormClassificationV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.cradleplatform.neptune.model.MCOptionV2
import com.cradleplatform.neptune.model.QRelationalEnum
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.VisibleConditionV2
import com.google.gson.JsonParseException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class FormV2DraftTypeConvertersTest {
    private val typeConverters = FormV2DraftTypeConverters()

    @Test
    fun `V2 answers preserve their API fields during a JSON round trip`() {
        val answers = listOf(
            FormAnswerV2(questionId = "string", answer = AnswerV2.createTextAnswer("Patient note", "comment")),
            FormAnswerV2(questionId = "integer", answer = AnswerV2.createNumericAnswer(42L)),
            FormAnswerV2(questionId = "decimal", answer = AnswerV2.createNumericAnswer(12.75)),
            FormAnswerV2(questionId = "single-choice", answer = AnswerV2.createMcAnswer(listOf(0))),
            FormAnswerV2(questionId = "multiple-select", answer = AnswerV2.createMcAnswer(listOf(0, 2))),
            FormAnswerV2(questionId = "date", answer = AnswerV2.createDateAnswer("2026-09-29")),
            FormAnswerV2(questionId = "time", answer = AnswerV2.createDateAnswer("18:30")),
            FormAnswerV2(
                id = "answer-id",
                questionId = "datetime",
                formSubmissionId = "submission-id",
                answer = AnswerV2.createDateAnswer("2026-09-29T18:30:00-07:00")
            )
        )

        val json = typeConverters.formAnswersV2ToJson(answers)
        val restoredAnswers = typeConverters.jsonToFormAnswersV2(json)

        assertEquals(answers, restoredAnswers)
        assertEquals(42L, restoredAnswers[1].answer.numericAnswer)
        assertEquals(12.75, restoredAnswers[2].answer.numericAnswer)
    }

    @Test
    fun `V2 template snapshot preserves questions options constraints and conditions`() {
        val template = formTemplate()

        val json = typeConverters.formTemplateV2ToJson(template)

        assertEquals(template, typeConverters.jsonToFormTemplateV2(json))
    }

    @Test
    fun `empty answers remain empty after a JSON round trip`() {
        val json = typeConverters.formAnswersV2ToJson(emptyList())

        assertEquals(emptyList<FormAnswerV2>(), typeConverters.jsonToFormAnswersV2(json))
    }

    @Test
    fun `invalid JSON is not silently restored as an empty draft`() {
        assertThrows(JsonParseException::class.java) {
            typeConverters.jsonToFormAnswersV2("not-json")
        }
        assertThrows(JsonParseException::class.java) {
            typeConverters.jsonToFormTemplateV2("not-json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            typeConverters.jsonToFormAnswersV2("null")
        }
        assertThrows(IllegalArgumentException::class.java) {
            typeConverters.jsonToFormTemplateV2("null")
        }
    }

    /** Uses the same Antenatal and symptom examples as the existing FormV2MapperTest fixture. */
    private fun formTemplate() = FormTemplateV2(
        id = "template-1",
        version = 2,
        archived = false,
        classification = FormClassificationV2(
            id = "class-1",
            name = mapOf("english" to "Antenatal", "french" to "Prenatal"),
            nameStringId = "classification-name"
        ),
        dateCreated = 1700000000L,
        questions = listOf(
            FormTemplateQuestionV2(
                id = "q1",
                formTemplateId = "template-1",
                questionType = QuestionTypeEnum.MULTIPLE_SELECT,
                order = 0,
                questionText = mapOf(
                    "english" to "Select symptoms",
                    "french" to "Selectionner les symptomes"
                ),
                questionStringId = "question-text",
                categoryIndex = 1,
                required = true,
                hasCommentAttached = true,
                allowFutureDates = false,
                allowPastDates = true,
                visibleCondition = listOf(
                    VisibleConditionV2(
                        0,
                        QRelationalEnum.LARGER_THAN,
                        AnswerV2.createNumericAnswer(18L)
                    )
                ),
                stringMaxLength = 100,
                stringMaxLines = 2,
                numMin = -10.5,
                numMax = 100.25,
                units = "kg",
                userQuestionId = "custom-question",
                mcOptions = listOf(
                    MCOptionV2("opt-headache", mapOf("english" to "Headache", "french" to "Maux de tete")),
                    MCOptionV2("opt-fever", mapOf("english" to "Fever", "french" to "Fievre"))
                )
            )
        )
    )
}
