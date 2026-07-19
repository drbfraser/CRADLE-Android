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
