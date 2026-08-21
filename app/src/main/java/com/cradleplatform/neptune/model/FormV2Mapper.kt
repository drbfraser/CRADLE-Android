package com.cradleplatform.neptune.model

/**
 * Converts between the existing local (V1-shaped) form models and the V2 wire models,
 * so [FormResponse]/[FormTemplate]/[McOption]/[Answer] and the local database schema
 * can stay unchanged while the app talks to the V2 API. See [Settings.useFormsV2].
 *
 * MCQ options are matched by array position (same as V1's [McOption.mcId]), since neither
 * [FormResponse] nor [Answer] has anywhere to store a V2 [MCOptionV2.stringId].
 */

fun FormResponse.toCreateSubmissionRequestV2(): CreateFormSubmissionRequestV2 {
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

private fun Answer.toAnswerV2(questionType: QuestionTypeEnum?): AnswerV2 = when {
    numericAnswer != null -> AnswerV2.createNumericAnswer(numericAnswer, comment)
    mcIdArrayAnswer != null -> AnswerV2.createMcAnswer(mcIdArrayAnswer, comment)
    textAnswer != null && (questionType == QuestionTypeEnum.DATE || questionType == QuestionTypeEnum.DATETIME) ->
        AnswerV2.createDateAnswer(textAnswer, comment)
    textAnswer != null -> AnswerV2.createTextAnswer(textAnswer, comment)
    else -> AnswerV2.createTextAnswer("", comment)
}

fun FormTemplateV2.toLocalFormTemplate(): FormTemplate = FormTemplate(
    version = version.toString(),
    archived = archived ?: false,
    dateCreated = dateCreated,
    id = id,
    formClassId = classification.id,
    formClassName = classification.name["english"],
    questions = questions?.map { it.toLocalQuestion() } ?: emptyList()
)

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

private fun VisibleConditionV2.toLocalVisibleCondition(): VisibleCondition = VisibleCondition(
    questionIndex = questionIndex,
    relation = relation.name,
    answerCondition = answers.toLocalAnswer()
)

private fun AnswerV2.toLocalAnswer(): Answer = when {
    numericAnswer != null -> Answer.createNumericAnswer(numericAnswer, comment ?: "")
    mcIdArrayAnswer != null -> Answer.createMcAnswer(mcIdArrayAnswer, comment ?: "")
    dateAnswer != null -> Answer.createTextAnswer(dateAnswer, comment ?: "")
    textAnswer != null -> Answer.createTextAnswer(textAnswer, comment ?: "")
    else -> Answer.createEmptyAnswer(comment ?: "")
}
