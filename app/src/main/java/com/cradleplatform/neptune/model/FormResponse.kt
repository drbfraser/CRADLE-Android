package com.cradleplatform.neptune.model

import android.util.Log
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import kotlin.IllegalArgumentException

/**
 *  A filled FormTemplate with a selected [language] containing [questionResponses] (answers) to questions.
 *  This class is mainly passed to submit(post) filled forms as [FormResponse]s to backend and is
 *      formatted according to backend's form object designs.
 *
 *  @param patientId The id for the patient this form is filled for
 *  @param formTemplate The template of the form this [FormResponse] is for
 *   The [FormTemplate] must be deeply non-null ([FormTemplate.verifyIntegrity] == true)
 *   (or in other words, must be valid. There should be no null parameters if parsed correctly)
 *  @param language The language selected for this response, must exist in original [FormTemplate]
 *  @param answers A Map of <questionId, Answer> to pass as responses
 *
 *  @throws IllegalArgumentException if passed parameters is invalid:
 *   1) [FormTemplate], [Question]s has null fields
 *   2) The [language] was not found in [FormTemplate]
 *   3) A Required [Question] (isRequired == true) has no response passed
 */
class FormResponse
@Throws(IllegalArgumentException::class)
constructor(
    patientId: String,
    formTemplate: FormTemplate,
    language: String,
    answers: Map<String, Answer>,
) {

    private val archived: Boolean
    private val formClassificationId: String
    private val dateCreated: Int
    @SerializedName("lang")
    private val language: String = language
    @SerializedName("questions")
    private val questionResponses: List<QuestionResponse>
    private val patientId = patientId

    init {

        // FormTemplate should not have any null values if parsed correctly
        if (!formTemplate.verifyIntegrity()) {
            throw IllegalArgumentException("FormTemplate passed for FormResponse creation has null parameter")
        }

        this@FormResponse.archived = formTemplate.archived!!
        this@FormResponse.formClassificationId = formTemplate.formClassId!!
        this@FormResponse.dateCreated = formTemplate.dateCreated!!
        this@FormResponse.questionResponses = createQuestionResponses(
            formTemplate.questions!!,
            language,
            answers
        )
    }

    @Suppress("ThrowsCount")
    private fun createQuestionResponses(
        questions: List<Question>,
        language: String,
        answers: Map<String, Answer>
    ): List<QuestionResponse> {
        val responseList: MutableList<QuestionResponse> = mutableListOf()

        questions.forEach { question ->

            if (!question.verifyIntegrity()) {
                throw IllegalArgumentException(
                    "Failed to create FormResponse: QuestionTemplate has null fields"
                )
            }

            val response = answers[question.questionId]
            val languageQuestionText = question.languageVersions?.find { it.language == language }?.questionText
                ?: throw IllegalArgumentException(
                    "Failed to create FormResponse: Language does not exist in FormTemplate"
                )

            if (response != null) {

                var mcOptionList: List<McOption> = question.languageVersions.find {
                    it.language == language
                }?.mcOptions ?: listOf()

                val questionResponse = QuestionResponse(
                    questionType = question.questionType!!,
                    hasCommentAttached = response.hasComment(),
                    answers = response,
                    required = question.required!!,
                    visibleCondition = question.visibleCondition!!,
                    isBlank = false, // blank refers to FormTemplates, not blank to FormResponses
                    formTemplateId = question.formTemplateId!!,
                    mcOptions = mcOptionList,
                    //TODO(mcOptions Return?)
                    questionIndex = question.questionIndex!!,
                    languageSpecificText = languageQuestionText
                )
                responseList.add(questionResponse)

            } else if (question.required == true) {
                throw IllegalArgumentException(
                    "Failed to create FormResponse: Required question does not have an answer"
                )
            } else {
                Log.w(TAG, "Answer Missing for questionId(${question.questionId}) in form ${question.formTemplateId}")
            }
        }

        return responseList
    }

    companion object {
        const val TAG = "FormResponse"
    }
}

/*
{
         "questionType":"CATEGORY",
         "hasCommentAttached":false,
         "answers":{
            "mcidArray":[

            ]
         },
         "required":true,
         "visibleCondition":[

         ],
         "isBlank":false,
         "formTemplateId":"26c87f59-c442-4b11-a4a6-34c2f853c156",
         "mcOptions":[

         ],
         "questionIndex":0,
         "questionText":"Referred by"
      },
 */
class QuestionResponse(
    @SerializedName("questionType") val questionType: QuestionTypeEnum,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean,
    @SerializedName("answers") var answers: Answer,
    @SerializedName("required") val required: Boolean,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>,
    @SerializedName("isBlank") val isBlank: Boolean = false,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("mcOptions") val mcOptions: List<McOption>,
    @SerializedName("questionIndex") val questionIndex: Int,
    @SerializedName("questionText") val languageSpecificText: String,
) : Serializable
