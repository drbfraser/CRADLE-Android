package com.cradleplatform.neptune.model

/** In-memory answers collected by the first V2 renderer. */
class FormV2AnswerState {
    private val answers = linkedMapOf<String, AnswerV2>()

    fun setAnswer(questionId: String?, answer: AnswerV2) {
        if (questionId != null) answers[questionId] = answer
    }

    fun getAnswer(questionId: String?): AnswerV2? = questionId?.let(answers::get)

    fun asFormAnswers(): List<FormAnswerV2> = answers.map { (questionId, answer) ->
        FormAnswerV2(questionId = questionId, answer = answer)
    }
}
