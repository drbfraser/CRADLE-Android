package com.cradleplatform.neptune.model

/** In-memory answers collected by the first V2 renderer. */
class FormV2AnswerState {
    private val answers = linkedMapOf<String, AnswerV2>()

    fun setAnswer(questionId: String?, answer: AnswerV2) {
        if (questionId != null) answers[questionId] = answer
    }

    fun removeAnswer(questionId: String?) {
        if (questionId != null) answers.remove(questionId)
    }

    fun getAnswer(questionId: String?): AnswerV2? = questionId?.let(answers::get)

    /** Replaces the in-memory answers when reopening a persisted draft. */
    fun replaceAnswers(formAnswers: List<FormAnswerV2>) {
        answers.clear()
        formAnswers.forEach { formAnswer ->
            answers[formAnswer.questionId] = formAnswer.answer
        }
    }

    fun toFormAnswers(): List<FormAnswerV2> = answers.map { (questionId, answer) ->
        FormAnswerV2(questionId = questionId, answer = answer)
    }
}
