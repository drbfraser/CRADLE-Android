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

}
