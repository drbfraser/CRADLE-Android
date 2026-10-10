package com.cradleplatform.neptune.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TimeZone

internal class FormV2SubmissionValidatorTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val nowSeconds = 1791288000L // 2026-10-06 12:00 UTC

    @Test
    fun `valid answers preserve V2 question IDs and option indices`() {
        val questions = listOf(
            question("q1", QuestionTypeEnum.INTEGER).copy(numMin = 0.0, numMax = 10.0),
            question("q2", QuestionTypeEnum.MULTIPLE_SELECT),
            question("heading", QuestionTypeEnum.CATEGORY).copy(required = false),
        )
        val answers = listOf(
            FormAnswerV2(questionId = "q1", answer = AnswerV2.createNumericAnswer(10L)),
            FormAnswerV2(questionId = "q2", answer = AnswerV2.createMcAnswer(listOf(0, 1))),
        )

        assertEquals(answers, success(validate(questions, answers)).answers)
    }

    @Test
    fun `required empty answers fail but optional empty answers are omitted`() {
        val text = question("q1", QuestionTypeEnum.STRING)
        val blank = FormAnswerV2(questionId = "q1", answer = AnswerV2.createTextAnswer("  "))

        assertError(validate(listOf(text), emptyList()), "q1", "An answer is required.")
        assertError(validate(listOf(text), listOf(blank)), "q1", "An answer is required.")
        assertTrue(success(validate(listOf(text.copy(required = false)), listOf(blank))).answers.isEmpty())
        assertError(
            validate(listOf(question("q1", QuestionTypeEnum.MULTIPLE_SELECT)), listOf(
                FormAnswerV2(questionId = "q1", answer = AnswerV2.createMcAnswer(emptyList()))
            )),
            "q1", "An answer is required."
        )
    }

    @Test
    fun `numbers must match integer type and inclusive bounds`() {
        val integer = question("q1", QuestionTypeEnum.INTEGER).copy(numMin = 0.0, numMax = 10.0)
        for (number in listOf(-1L, 11L, 1.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(validate(listOf(integer), listOf(answer(AnswerV2.createNumericAnswer(number))))
                is FormV2SubmissionValidationResult.Error)
        }
        for (number in listOf(0L, 10L)) {
            success(validate(listOf(integer), listOf(answer(AnswerV2.createNumericAnswer(number)))))
        }
        success(validate(
            listOf(integer.copy(questionType = QuestionTypeEnum.DECIMAL)),
            listOf(answer(AnswerV2.createNumericAnswer(1.5)))
        ))
    }

    @Test
    fun `text respects character and line limits without trimming answers`() {
        val text = question("q1", QuestionTypeEnum.STRING).copy(stringMaxLength = 4, stringMaxLines = 1)
        assertError(validate(listOf(text), listOf(answer(AnswerV2.createTextAnswer("hello")))),
            "q1", "Text exceeds the maximum of 4 characters.")
        assertError(validate(listOf(text), listOf(answer(AnswerV2.createTextAnswer("a\nb")))),
            "q1", "Text exceeds the maximum of 1 lines.")
        val original = answer(AnswerV2.createTextAnswer(" ok "))
        assertEquals(listOf(original), success(validate(listOf(text), listOf(original))).answers)
        // Platform's Python len() counts this supplementary character as one character.
        success(validate(listOf(text.copy(stringMaxLength = 1)), listOf(
            answer(AnswerV2.createTextAnswer("\uD83D\uDE00"))
        )))
    }

    @Test
    fun `multiple choice and select enforce valid unique option indices`() {
        val multipleChoice = question("q1", QuestionTypeEnum.MULTIPLE_CHOICE)
        for (indices in listOf(listOf(-1), listOf(2), listOf(0, 1))) {
            assertTrue(validate(listOf(multipleChoice), listOf(answer(AnswerV2.createMcAnswer(indices))))
                is FormV2SubmissionValidationResult.Error)
        }
        assertError(validate(listOf(multipleChoice.copy(questionType = QuestionTypeEnum.MULTIPLE_SELECT)), listOf(
            answer(AnswerV2.createMcAnswer(listOf(0, 0)))
        )), "q1", "Selected options must be unique.")
    }

    @Test
    fun `answers cannot refer to unknown questions or duplicate question IDs`() {
        val text = question("q1", QuestionTypeEnum.STRING)
        val unknown = FormAnswerV2(questionId = "other", answer = AnswerV2.createTextAnswer("ok"))
        assertError(validate(listOf(text), listOf(unknown)), "other", "Question does not belong to this template.")
        val original = answer(AnswerV2.createTextAnswer("ok"))
        assertError(validate(listOf(text), listOf(original, original)), null, "A question has duplicate answers.")
    }

    @Test
    fun `answer type must match the question and contain only one value`() {
        val integer = question("q1", QuestionTypeEnum.INTEGER)
        assertError(validate(listOf(integer), listOf(answer(AnswerV2.createTextAnswer("1")))),
            "q1", "A numeric answer is required.")
        val mixed = AnswerV2.createNumericAnswer(1).copy(textAnswer = "1")
        assertError(validate(listOf(integer), listOf(answer(mixed))),
            "q1", "Answer must contain exactly one value.")
    }

    @Test
    fun `date conversion creates a new Unix seconds answer and preserves the draft and comment`() {
        val original = answer(AnswerV2.createDateAnswer("2026-10-06", "Patient reported date"))
        val result = success(validate(listOf(question("q1", QuestionTypeEnum.DATE)), listOf(original)))

        assertEquals("1791244800", result.answers.single().answer.dateAnswer)
        assertEquals("Patient reported date", result.answers.single().answer.comment)
        assertEquals("2026-10-06", original.answer.dateAnswer)
    }

    @Test
    fun `datetime conversion uses the device time zone`() {
        val result = success(validate(
            listOf(question("q1", QuestionTypeEnum.DATETIME)),
            listOf(answer(AnswerV2.createDateAnswer("2026-10-06 05:00"))),
            TimeZone.getTimeZone("America/Vancouver"),
        ))
        assertEquals(nowSeconds.toString(), result.answers.single().answer.dateAnswer)
    }

    @Test
    fun `invalid dates and incomplete datetimes fail strict parsing`() {
        for (date in listOf("2026-02-30", "2026-2-01", "2026-10-06 trailing")) {
            assertError(validate(listOf(question("q1", QuestionTypeEnum.DATE)), listOf(
                answer(AnswerV2.createDateAnswer(date))
            )), "q1", "Date or time is invalid.")
        }
        assertError(validate(listOf(question("q1", QuestionTypeEnum.DATETIME)), listOf(
            answer(AnswerV2.createDateAnswer("2026-10-06"))
        )), "q1", "Date or time is invalid.")
        assertError(validate(listOf(question("q1", QuestionTypeEnum.DATETIME)), listOf(
            answer(AnswerV2.createDateAnswer("2026-03-08 02:30"))
        ), TimeZone.getTimeZone("America/Vancouver")), "q1", "Date or time is invalid.")
    }

    @Test
    fun `date constraints compare the final instant to now like Platform`() {
        val date = question("q1", QuestionTypeEnum.DATE).copy(allowPastDates = false)
        assertError(validate(listOf(date), listOf(answer(AnswerV2.createDateAnswer("2026-10-06")))),
            "q1", "Past dates are not allowed.")
        val datetime = question("q1", QuestionTypeEnum.DATETIME).copy(allowFutureDates = false)
        assertError(validate(listOf(datetime), listOf(answer(AnswerV2.createDateAnswer("2026-10-06 12:01")))),
            "q1", "Future dates are not allowed.")
        success(validate(listOf(datetime), listOf(answer(AnswerV2.createDateAnswer(nowSeconds.toString())))))
    }

    @Test
    fun `optional TIME can be omitted but submitted TIME fails before submission`() {
        success(validate(listOf(question("q1", QuestionTypeEnum.TIME).copy(required = false)), emptyList()))
        assertError(
            validate(
                listOf(question("q1", QuestionTypeEnum.TIME).copy(required = false)),
                listOf(answer(AnswerV2.createDateAnswer("09:30")))
            ),
            "q1",
            "TIME questions are not supported by the V2 submission API yet."
        )
    }

    @Test
    fun `conditional questions fail before submission`() {
        val conditional = question("q1", QuestionTypeEnum.STRING).copy(visibleCondition = listOf(
            VisibleConditionV2(0, QRelationalEnum.EQUAL_TO, AnswerV2.createTextAnswer("yes"))
        ))
        assertError(validate(listOf(conditional), emptyList()),
            "q1", "Conditional questions are not supported by this V2 renderer yet.")
    }

    @Test
    fun `missing question IDs and required category headings cannot produce a valid submission`() {
        assertError(validate(listOf(question("q1", QuestionTypeEnum.STRING).copy(id = null)), emptyList()),
            null, "Template question has no ID.")
        assertError(validate(listOf(question("heading", QuestionTypeEnum.CATEGORY)), emptyList()),
            "heading", "Category headings cannot require an answer.")
    }

    @Test
    fun `duplicate template question IDs cannot make answer routing ambiguous`() {
        assertError(
            validate(
                listOf(
                    question("q1", QuestionTypeEnum.STRING),
                    question("q1", QuestionTypeEnum.INTEGER),
                ),
                emptyList(),
            ),
            "q1",
            "Template contains duplicate question IDs.",
        )
    }

    private fun answer(value: AnswerV2) = FormAnswerV2(questionId = "q1", answer = value)

    private fun validate(
        questions: List<FormTemplateQuestionV2>,
        answers: List<FormAnswerV2>,
        timeZone: TimeZone = utc,
    ) = FormV2SubmissionValidator.validate(
        FormTemplateV2(
            id = "template-1",
            version = 1,
            classification = FormClassificationV2("class-1", mapOf("english" to "Antenatal"), null),
            dateCreated = 1700000000L,
            questions = questions,
        ),
        answers, nowSeconds, timeZone,
    )

    private fun question(id: String, type: QuestionTypeEnum) = FormTemplateQuestionV2(
        id = id,
        formTemplateId = "template-1",
        questionType = type,
        order = 0,
        questionText = mapOf("english" to "Select symptoms"),
        questionStringId = null,
        categoryIndex = null,
        required = true,
        allowFutureDates = null,
        allowPastDates = null,
        stringMaxLength = null,
        stringMaxLines = null,
        numMin = null,
        numMax = null,
        units = null,
        userQuestionId = null,
        mcOptions = listOf(
            MCOptionV2("opt-headache", mapOf("english" to "Headache")),
            MCOptionV2("opt-fever", mapOf("english" to "Fever")),
        ),
    )

    private fun success(result: FormV2SubmissionValidationResult): FormV2SubmissionValidationResult.Success {
        assertTrue(result is FormV2SubmissionValidationResult.Success, result.toString())
        return result as FormV2SubmissionValidationResult.Success
    }

    private fun assertError(result: FormV2SubmissionValidationResult, questionId: String?, message: String) {
        assertEquals(FormV2SubmissionValidationResult.Error(questionId, message), result)
    }
}
