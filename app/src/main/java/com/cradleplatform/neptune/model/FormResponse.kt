package com.cradleplatform.neptune.model

import android.util.Log
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.lang.IllegalArgumentException

/**
 * The [FormTemplate] must be deeply non-null ([FormTemplate.deepNullCheck] == true)
 */

class FormResponse(
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

        if (!formTemplate.deepNullCheck()) {
            throw NullPointerException("FormTemplate passed for FormResponse creation has null parameter")
        }

        this@FormResponse.archived = formTemplate.archived!!
        this@FormResponse.formClassificationId = formTemplate.formClassId!!
        this@FormResponse.dateCreated = formTemplate.dateCreated!!
        this@FormResponse.questionResponses = createQuestionResponses(
            formTemplate.questions!!,
            language,
            answers
        )

        Log.e(TAG, "SIZE: ${formTemplate.questions.size} ${questionResponses.size}")

        if (questionResponses.size != formTemplate.questions.size) {
            // FIXME: re-evaluate error handling
            //throw IllegalArgumentException()
            Log.e(TAG, "FormTemplate.questions size mismatch response size in FormResponse")
        }
/*
        val formStr = GsonBuilder().setPrettyPrinting().create().toJson(answers)
        val formChunks = formStr.chunked(2048)
        formChunks.forEach{
            Log.e(TAG,it)
        }
   */
    }

    private fun createQuestionResponses(
        questions: List<Question>,
        language: String,
        answers: Map<String, Answer>
    ): List<QuestionResponse> {
        val responseList: MutableList<QuestionResponse> = mutableListOf()

        Log.e(TAG, "answers sizes: ${answers.count()}")

        questions.forEach { question ->

            Log.e(TAG, "FOREACH: question[${question.questionId!!}]")

            if (!question.deepNullCheck()) {
                Log.w(TAG, "Null required field found during QuestionResponse creation, skipping question")
                return@forEach
            }

            val response = answers[question.questionId]
            val languageQuestionText = question.languageVersions?.find { it.language == language }?.questionText
            if (languageQuestionText == null) {
                throw IllegalArgumentException(
                    "Failed to create FormResponse: Language does not exist in FormTemplate"
                )
                return@forEach
            }

            if (response != null) {

                val questionResponse = QuestionResponse(
                    questionType = question.questionType!!,
                    hasCommentAttached = question.hasCommentAttached!!,
                    answers = response,
                    required = question.required!!,
                    visibleCondition = question.visibleCondition!!,
                    isBlank = question.isBlank!!,
                    formTemplateId = question.formTemplateId!!,
                    mcOptions = question.mcOptions!!,
                    questionIndex = question.questionIndex!!,
                    languageSpecificText = languageQuestionText
                )
                responseList.add(questionResponse)
                Log.e(TAG, "ADDING RESPONSE")
            } else if (question.required == true) {
                Log.e(TAG, "Failed to create FormResponse: Required question does not have an answer")
            /*
                throw IllegalArgumentException(
                    "Failed to create FormResponse: Required question does not have an answer"
                )*/
            } else {
                Log.w(TAG, "Answer Missing for questionId(${question.questionId})")
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
    @SerializedName("isBlank") val isBlank: Boolean,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("mcOptions") val mcOptions: List<McOption>,
    @SerializedName("questionIndex") val questionIndex: Int,
    @SerializedName("questionText") val languageSpecificText: String,
) : Serializable
