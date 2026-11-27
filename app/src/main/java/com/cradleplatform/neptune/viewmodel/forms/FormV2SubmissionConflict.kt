package com.cradleplatform.neptune.viewmodel.forms

import com.cradleplatform.neptune.model.CreateFormSubmissionRequestV2
import com.cradleplatform.neptune.model.FormSubmissionWithAnswersV2
import com.cradleplatform.neptune.model.FormTemplateV2

/** Snapshot for review; confirmation is requested again after process recreation or a stale revision. */
data class FormV2SubmissionConflict(
    val draftRequest: CreateFormSubmissionRequestV2,
    val normalizedRequest: CreateFormSubmissionRequestV2,
    val server: FormSubmissionWithAnswersV2,
    val timeZoneId: String
) {
    internal fun validationTemplate(template: FormTemplateV2): FormTemplateV2 {
        val storedAnswers = server.answers.orEmpty().associateBy { it.questionId }
        val unchangedDates = normalizedRequest.answers.filter { local ->
            local.answer.dateAnswer != null &&
                local.answer.dateAnswer == storedAnswers[local.questionId]?.answer?.dateAnswer
        }.map { it.questionId }.toSet()
        // Platform permits unchanged historical dates when replacing other answers.
        return template.copy(questions = template.questions?.map { question ->
            if (question.id in unchangedDates) question.copy(allowPastDates = null, allowFutureDates = null)
            else question
        })
    }
}
