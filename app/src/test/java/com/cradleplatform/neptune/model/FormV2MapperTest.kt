package com.cradleplatform.neptune.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * some basic tets for the FormV2Mapper functions, to ensure that the mapping between V1 and V2 models is correct.
 * uses some default values from other tests.
 */
internal class FormV2MapperTest {

    private fun stringQuestion(id: String, index: Int) = Question(
        id = id,
        allowPastDates = true,
        allowFutureDates = true,
        visibleCondition = emptyList(),
        isBlank = true,
        formTemplateId = "template-1",
        questionIndex = index,
        numMin = null,
        numMax = null,
        stringMaxLength = null,
        stringMaxLines = null,
        questionType = QuestionTypeEnum.STRING,
        hasCommentAttached = false,
        required = true,
        languageVersions = listOf(
            QuestionLangVersion("English", id, "How are you feeling?", null, null)
        )
    )

    private fun dateQuestion(id: String, index: Int) = stringQuestion(id, index).copy(
        questionType = QuestionTypeEnum.DATE
    )

    private fun mcQuestion(id: String, index: Int) = Question(
        id = id,
        allowPastDates = true,
        allowFutureDates = true,
        visibleCondition = emptyList(),
        isBlank = true,
        formTemplateId = "template-1",
        questionIndex = index,
        numMin = null,
        numMax = null,
        stringMaxLength = null,
        stringMaxLines = null,
        questionType = QuestionTypeEnum.MULTIPLE_CHOICE,
        hasCommentAttached = false,
        required = false,
        languageVersions = listOf(
            QuestionLangVersion(
                "English", id, "Select symptoms", null,
                mcOptions = listOf(McOption(0, "Headache"), McOption(1, "Fever"))
            )
        )
    )

    private fun template(questions: List<Question>) = FormTemplate(
        version = "1",
        archived = false,
        dateCreated = 1700000000L,
        id = "template-1",
        formClassId = "class-1",
        formClassName = "Antenatal",
        questions = questions
    )

    @Test
    fun `toCreateSubmissionRequestV2 maps answers by question id and preserves answer shape`() {
        val formResponse = FormResponse(
            patientId = "patient-1",
            formTemplate = template(listOf(stringQuestion("q1", 0), mcQuestion("q2", 1))),
            language = "English",
            answers = mapOf(
                "q1" to Answer.createTextAnswer("ok"),
                "q2" to Answer.createMcAnswer(listOf(0)),
            ),
            saveResponseToSendLater = true,
        )

        val request = formResponse.toCreateSubmissionRequestV2()

        assertEquals("template-1", request.formTemplateId)
        assertEquals("patient-1", request.patientId)
        assertEquals("English", request.lang)
        assertEquals(2, request.answers.size)

        val textAnswer = request.answers.first { it.questionId == "q1" }
        assertEquals("ok", textAnswer.answer.textAnswer)

        val mcAnswer = request.answers.first { it.questionId == "q2" }
        assertEquals(listOf(0), mcAnswer.answer.mcIdArrayAnswer)
    }

    @Test
    fun `toCreateSubmissionRequestV2 maps a text answer on a DATE question to the date field`() {
        val formResponse = FormResponse(
            patientId = "patient-1",
            formTemplate = template(listOf(dateQuestion("q1", 0))),
            language = "English",
            answers = mapOf("q1" to Answer.createTextAnswer("2025-10-30")),
            saveResponseToSendLater = true,
        )

        val dateAnswer = formResponse.toCreateSubmissionRequestV2().answers.first { it.questionId == "q1" }

        assertEquals("2025-10-30", dateAnswer.answer.dateAnswer)
        assertNull(dateAnswer.answer.textAnswer)
    }

    @Test
    fun `toLocalFormTemplate maps question order and positional mc options`() {
        val templateV2 = FormTemplateV2(
            id = "template-1",
            version = 3,
            archived = false,
            classification = FormClassificationV2(
                id = "class-1",
                name = mapOf("english" to "Antenatal", "french" to "Prenatal"),
                nameStringId = "str-1",
            ),
            dateCreated = 1700000000L,
            questions = listOf(
                FormTemplateQuestionV2(
                    id = "q1",
                    formTemplateId = "template-1",
                    questionType = QuestionTypeEnum.MULTIPLE_CHOICE,
                    order = 0,
                    questionText = mapOf("english" to "Select symptoms"),
                    questionStringId = "qs1",
                    categoryIndex = null,
                    required = true,
                    hasCommentAttached = false,
                    allowFutureDates = null,
                    allowPastDates = null,
                    visibleCondition = emptyList(),
                    stringMaxLength = null,
                    stringMaxLines = null,
                    numMin = null,
                    numMax = null,
                    units = null,
                    userQuestionId = null,
                    mcOptions = listOf(
                        MCOptionV2("opt-headache", mapOf("english" to "Headache")),
                        MCOptionV2("opt-fever", mapOf("english" to "Fever")),
                    )
                )
            )
        )

        val local = templateV2.toLocalFormTemplate()

        assertEquals("template-1", local.id)
        assertEquals("3", local.version)
        assertEquals("class-1", local.formClassId)
        assertEquals("Antenatal", local.formClassName)

        val question = local.questions?.first()
        assertEquals("q1", question?.id)
        assertEquals(0, question?.questionIndex)
        assertEquals(QuestionTypeEnum.MULTIPLE_CHOICE, question?.questionType)

        val englishVersion = question?.languageVersions?.first { it.language == "english" }
        assertEquals("Select symptoms", englishVersion?.questionText)
        assertEquals(0, englishVersion?.mcOptions?.get(0)?.mcId)
        assertEquals("Headache", englishVersion?.mcOptions?.get(0)?.opt)
        assertEquals(1, englishVersion?.mcOptions?.get(1)?.mcId)
        assertEquals("Fever", englishVersion?.mcOptions?.get(1)?.opt)
    }
}
