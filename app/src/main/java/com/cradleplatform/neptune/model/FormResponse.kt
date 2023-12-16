package com.cradleplatform.neptune.model

import android.util.Log
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

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
@Entity(
    indices = [
        Index(value = ["formResponseId"], unique = true),
        Index(value = ["patientId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("patientId"),
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        ),
    ],
)
class FormResponse
@Throws(IllegalArgumentException::class)
constructor(
    @PrimaryKey(autoGenerate = true)
    var formResponseId: Long = 0,
    patientId: String,
    var formTemplate: FormTemplate,
    language: String,
    var answers: Map<String, Answer>,
    var saveResponseToSendLater: Boolean = false
) {
    // Fields that should be exposed to GSON serialization (aka fields that are necessary for the
    // form when being sent through Wifi) should be marked with @Expose.
    // Fields that are only relevant locally (e.g. "formTemplate") should NOT be marked @Expose.
    @Expose
    var archived: Boolean
    @Expose
    var formClassificationId: String
    var formClassificationName: String?
    @Expose
    var dateCreated: Long
    @Expose
    @SerializedName("lang")
    var language: String = language
    @Expose
    @SerializedName("questions")
    var questionResponses: List<QuestionResponse>
    @Expose
    var patientId = patientId
    var dateEdited: Long

    init {

        // FormTemplate should not have any null values if parsed correctly
        if (!saveResponseToSendLater && !formTemplate.verifyIntegrity()) {
            throw IllegalArgumentException("FormTemplate passed for FormResponse creation has null parameter")
        }

        this@FormResponse.archived = formTemplate.archived!!
        this@FormResponse.formClassificationId = formTemplate.formClassId!!
        this@FormResponse.formClassificationName = formTemplate.formClassName
        this@FormResponse.dateCreated = formTemplate.dateCreated!!
        this@FormResponse.questionResponses = createQuestionResponses(
            formTemplate.questions!!,
            language,
            answers
        )

        this@FormResponse.dateEdited = System.currentTimeMillis()
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
            val languageVersion = question.languageVersions?.find { it.language == language }
            val languageQuestionText = languageVersion?.questionText
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
                    questionIndex = question.questionIndex!!,
                    languageSpecificText = languageQuestionText
                )
                responseList.add(questionResponse)
            } else if (question.required == true) {
                throw IllegalArgumentException(

                    "Failed to create FormResponse: Required question does not have an answer $question"
                )
            } else {
                Log.w(TAG, "Answer Missing for questionId(${question.questionId}) in form ${question.formTemplateId}")
            }
        }

        return responseList
    }

    override operator fun equals(
        other: Any?
    ): Boolean {
        return when (other) {
            is FormResponse -> {
                this.formResponseId == other.formResponseId
            }
            else -> false
        }
    }

    override fun hashCode(): Int {
        return super.hashCode()
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
