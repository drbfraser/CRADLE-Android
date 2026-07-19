package com.cradleplatform.neptune.model
import com.cradleplatform.neptune.api.model.v2.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.api.model.v2.FormAnswerV2

/**
 * Converts between the existing v1 form models and the V2 models,
 * so that the local db can stay unchanged while the v2 API is used 
 */

/**
  * Converts a FormResponse to a CreateFormSubmissionRequestV2, mapping answers to the appropriate AnswerV2 type based on the question type.
  */
fun FormResponse.toCreateSubmissionRequestV2(): CreateFormSubmissionRequestV2 {
    // Get the question type for each question id from the form template
    val questionTypeById = formTemplate.questions?.associate { it.id to it.questionType } ?: emptyMap()

    return CreateFormSubmissionRequestV2(
        formTemplateId = requireNotNull(formTemplate.id) { "formTemplate.id was null" },
        patientId = patientId,
        lang = language,
        answers = answers.map { (questionId, answer) ->
            FormAnswerV2(
                questionId = questionId,
                answer = answer.toAnswerV2(questionTypeById[questionId])
            )
        }
    )
}

// Converts an answer to an answerv2, mapping the answer to the appropriate type based on the question type.
private fun Answer.toAnswerV2(questionType: QuestionTypeEnum?): AnswerV2 = when {
    numericAnswer != null -> AnswerV2.createNumericAnswer(numericAnswer, comment)
    mcIdArrayAnswer != null -> AnswerV2.createMcAnswer(mcIdArrayAnswer, comment)
    textAnswer != null && (questionType == QuestionTypeEnum.DATE || questionType == QuestionTypeEnum.DATETIME) ->
        AnswerV2.createDateAnswer(textAnswer, comment)
    textAnswer != null -> AnswerV2.createTextAnswer(textAnswer, comment)
    else -> AnswerV2.createTextAnswer("", comment)
}

// Converts a FormTemplateV2 to a FormTemplate, mapping questions and their language versions.
fun FormTemplateV2.toLocalFormTemplate(): FormTemplate = FormTemplate(
    version = version.toString(),
    archived = archived ?: false,
    dateCreated = dateCreated,
    id = id,
    formClassId = classification.id,
    formClassName = classification.name["english"],
    questions = questions?.map { it.toLocalQuestion() } ?: emptyList()
)

// Converts a FormTemplateQuestionV2 to a Question, mapping language versions and multiple choice options.
private fun FormTemplateQuestionV2.toLocalQuestion(): Question {
    val languageVersions = questionText.keys.map { lang ->
        QuestionLangVersion(
            language = lang,
            parentId = id,
            questionText = questionText[lang],
            questionTextId = null,
            mcOptions = mcOptions?.mapIndexed { index, option ->
                McOption(mcId = index, opt = option.translations[lang] ?: option.translations["english"])
            }
        )
    }

    return Question(
        id = id,
        allowPastDates = allowPastDates,
        allowFutureDates = allowFutureDates,
        visibleCondition = visibleCondition?.map { it.toLocalVisibleCondition() },
        isBlank = true,
        formTemplateId = formTemplateId,
        questionIndex = order,
        numMin = numMin,
        numMax = numMax,
        stringMaxLength = stringMaxLength,
        stringMaxLines = stringMaxLines,
        questionType = questionType,
        hasCommentAttached = hasCommentAttached ?: false,
        required = required,
        languageVersions = languageVersions
    )
}

// Converts a VisibleConditionV2 to a VisibleCondition, mapping the relation and answers.
private fun VisibleConditionV2.toLocalVisibleCondition(): VisibleCondition = VisibleCondition(
    questionIndex = questionIndex,
    relation = relation.name,
    answerCondition = answers.toLocalAnswer()
)

// Converts a answerV2 to a answer, mapping the answer to the appropriate type based on which field is not null.
private fun AnswerV2.toLocalAnswer(): Answer = when {
    numericAnswer != null -> Answer.createNumericAnswer(numericAnswer, comment ?: "")
    mcIdArrayAnswer != null -> Answer.createMcAnswer(mcIdArrayAnswer, comment ?: "")
    dateAnswer != null -> Answer.createTextAnswer(dateAnswer, comment ?: "")
    textAnswer != null -> Answer.createTextAnswer(textAnswer, comment ?: "")
    else -> Answer.createEmptyAnswer(comment ?: "")
}

