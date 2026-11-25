package com.cradleplatform.neptune.model

object FormV2SubmissionComparison {
    fun matches(request: CreateFormSubmissionRequestV2, saved: FormSubmissionWithAnswersV2): Boolean {
        if (!matchesIdentity(request, saved)) {
            return false
        }

        val savedAnswers = saved.answers.orEmpty()
        val answersByQuestion = savedAnswers.associateBy { it.questionId }
        return saved.answers != null && savedAnswers.size == request.answers.size &&
            answersByQuestion.size == savedAnswers.size &&
            request.answers.distinctBy { it.questionId }.size == request.answers.size &&
            request.answers.all { expected ->
                answersByQuestion[expected.questionId]?.answer?.let { sameAnswer(expected.answer, it) } == true
            }
    }

    fun matchesIdentity(request: CreateFormSubmissionRequestV2, saved: FormSubmissionWithAnswersV2): Boolean =
        request.id == saved.id && request.patientId == saved.patientId &&
            request.formTemplateId == saved.formTemplateId && request.lang == saved.lang && !saved.archived

    private fun sameAnswer(first: AnswerV2, second: AnswerV2): Boolean =
        sameNumber(first.numericAnswer, second.numericAnswer) &&
            first.textAnswer == second.textAnswer && first.dateAnswer == second.dateAnswer &&
            first.comment == second.comment &&
            first.mcIdArrayAnswer?.sorted() == second.mcIdArrayAnswer?.sorted()

    private fun sameNumber(first: Number?, second: Number?): Boolean {
        if (first == null || second == null) return first == second
        // Compare values rather than Long/Double/Gson Number implementation classes.
        val firstValue = first.toString().toBigDecimalOrNull()
        val secondValue = second.toString().toBigDecimalOrNull()
        return firstValue != null && secondValue != null && firstValue.compareTo(secondValue) == 0
    }
}
