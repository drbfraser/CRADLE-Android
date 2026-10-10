package com.cradleplatform.neptune.model

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed class FormV2SubmissionValidationResult {
    data class Success(val answers: List<FormAnswerV2>) : FormV2SubmissionValidationResult()
    data class Error(val questionId: String?, val message: String) : FormV2SubmissionValidationResult()
}

object FormV2SubmissionValidator {
    fun validate(
        template: FormTemplateV2,
        answers: List<FormAnswerV2>,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): FormV2SubmissionValidationResult {
        val questions = template.questions.orEmpty()
        val duplicateQuestionId = questions.mapNotNull { it.id }
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        val questionsById = questions.associateBy { it.id }
        val answersById = answers.associateBy { it.questionId }
        val duplicateError = when {
            duplicateQuestionId != null -> FormV2SubmissionValidationResult.Error(
                duplicateQuestionId,
                "Template contains duplicate question IDs."
            )
            answersById.size != answers.size ->
                FormV2SubmissionValidationResult.Error(null, "A question has duplicate answers.")
            else -> null
        }
        if (duplicateError != null) return duplicateError
        answers.firstOrNull { it.questionId !in questionsById }?.let {
            return FormV2SubmissionValidationResult.Error(it.questionId, "Question does not belong to this template.")
        }

        val submissionAnswers = mutableListOf<FormAnswerV2>()
        for (question in questions.sortedBy { it.order }) {
            val templateError = validateQuestion(question)
            if (templateError != null) {
                return FormV2SubmissionValidationResult.Error(question.id, templateError)
            }
            if (question.questionType == QuestionTypeEnum.CATEGORY) continue

            val formAnswer = answersById[question.id]
            if (formAnswer == null || isEmptyAnswer(formAnswer.answer)) {
                if (question.required) {
                    return FormV2SubmissionValidationResult.Error(question.id, "An answer is required.")
                }
                continue
            }

            if (question.questionType == QuestionTypeEnum.TIME) {
                return FormV2SubmissionValidationResult.Error(
                    question.id,
                    "TIME questions are not supported by the V2 submission API yet."
                )
            }

            val answer = formAnswer.answer
            val error = validateAnswer(question, answer)
            if (error != null) {
                return FormV2SubmissionValidationResult.Error(question.id, error)
            }
            if (question.questionType == QuestionTypeEnum.DATE || question.questionType == QuestionTypeEnum.DATETIME) {
                val seconds = dateToSeconds(answer.dateAnswer.orEmpty(), question.questionType, timeZone)
                    ?: return FormV2SubmissionValidationResult.Error(question.id, "Date or time is invalid.")

                val dateError = when {
                    question.allowPastDates == false && seconds < nowSeconds -> "Past dates are not allowed."
                    question.allowFutureDates == false && seconds > nowSeconds -> "Future dates are not allowed."
                    else -> null
                }
                if (dateError != null) {
                    return FormV2SubmissionValidationResult.Error(question.id, dateError)
                }
                submissionAnswers.add(formAnswer.copy(
                    answer = AnswerV2.createDateAnswer(seconds.toString(), answer.comment)
                ))
            } else {
                submissionAnswers.add(formAnswer)
            }
        }
        return FormV2SubmissionValidationResult.Success(submissionAnswers)
    }

    private fun validateQuestion(question: FormTemplateQuestionV2): String? = when {
        question.id.isNullOrBlank() -> "Template question has no ID."
        !question.visibleCondition.isNullOrEmpty() -> "Conditional questions are not supported by this V2 renderer yet."
        question.questionType == QuestionTypeEnum.CATEGORY && question.required ->
            "Category headings cannot require an answer."
        else -> null
    }

    private fun isEmptyAnswer(answer: AnswerV2): Boolean =
        answer.numericAnswer == null && answer.textAnswer.isNullOrBlank() &&
            answer.mcIdArrayAnswer.isNullOrEmpty() && answer.dateAnswer.isNullOrBlank()

    private fun validateAnswer(question: FormTemplateQuestionV2, answer: AnswerV2): String? {
        val valueCount = listOf(
            answer.numericAnswer, answer.textAnswer, answer.mcIdArrayAnswer, answer.dateAnswer
        ).count { it != null }
        if (valueCount != 1) return "Answer must contain exactly one value."

        return when (question.questionType) {
            QuestionTypeEnum.INTEGER, QuestionTypeEnum.DECIMAL -> validateNumber(question, answer.numericAnswer)
            QuestionTypeEnum.STRING -> validateText(question, answer.textAnswer)
            QuestionTypeEnum.MULTIPLE_CHOICE, QuestionTypeEnum.MULTIPLE_SELECT ->
                validateChoices(question, answer.mcIdArrayAnswer)
            QuestionTypeEnum.DATE, QuestionTypeEnum.DATETIME ->
                if (answer.dateAnswer == null) "A date answer is required." else null
            else -> "Question type is not supported for submission."
        }
    }

    private fun validateNumber(question: FormTemplateQuestionV2, number: Number?): String? {
        val value = number?.toDouble() ?: return "A numeric answer is required."
        return when {
            !value.isFinite() -> "Number must be finite."
            question.questionType == QuestionTypeEnum.INTEGER && value % 1.0 != 0.0 -> "An integer answer is required."
            question.numMin != null && value < question.numMin -> "Number is below the minimum of ${question.numMin}."
            question.numMax != null && value > question.numMax -> "Number exceeds the maximum of ${question.numMax}."
            else -> null
        }
    }

    private fun validateText(question: FormTemplateQuestionV2, text: String?): String? {
        if (text == null) return "A text answer is required."
        return when {
            // Match the server's Unicode character count
            question.stringMaxLength != null && text.codePointCount(0, text.length) > question.stringMaxLength ->
                "Text exceeds the maximum of ${question.stringMaxLength} characters."
            question.stringMaxLines != null && text.lines().size > question.stringMaxLines ->
                "Text exceeds the maximum of ${question.stringMaxLines} lines."
            else -> null
        }
    }

    private fun validateChoices(question: FormTemplateQuestionV2, selectedIndices: List<Int>?): String? {
        if (selectedIndices == null) return "A choice answer is required."
        return when {
            question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE && selectedIndices.size != 1 ->
                "Select exactly one option."
            selectedIndices.distinct().size != selectedIndices.size -> "Selected options must be unique."
            selectedIndices.any { it !in question.mcOptions.orEmpty().indices } -> "Selected option does not exist."
            else -> null
        }
    }

    private fun dateToSeconds(value: String, type: QuestionTypeEnum, timeZone: TimeZone): Long? {
        value.toLongOrNull()?.let { return it }
        val pattern = if (type == QuestionTypeEnum.DATE) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm"
        val format = SimpleDateFormat(pattern, Locale.ROOT).apply {
            isLenient = false
            this.timeZone = timeZone
        }
        val position = ParsePosition(0)
        val date = format.parse(value, position) ?: return null
        // Reject partial input, normalized dates, and nonexistent local DST times
        if (position.index != value.length || format.format(date) != value) return null
        return date.time / 1000
    }
}
