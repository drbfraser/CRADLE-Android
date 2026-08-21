package com.cradleplatform.neptune.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Represents the V2 form model, used for the new form submission flow.
 * Mimics the v1 form model but adds some changes to it as well, and mimics the server side model.
 * The class names come from the backend to make migration easier.
 */

typealias MultiLangText = Map<String, String>

enum class QRelationalEnum {
    LARGER_THAN,
    SMALLER_THAN,
    EQUAL_TO,
    CONTAINS,
}

/**
 *  same as before but with the addition of the DATE/DATETIME answer field.
 */
data class AnswerV2 private constructor(
    @Expose @SerializedName("number") val numericAnswer: Number?,
    @Expose @SerializedName("text") val textAnswer: String?,
    @Expose @SerializedName("mcIdArray") val mcIdArrayAnswer: List<Int>?,
    @Expose @SerializedName("date") val dateAnswer: String?,
    @Expose @SerializedName("comment") val comment: String?,
) : Serializable {

    companion object {
        fun createNumericAnswer(number: Number, comment: String? = null): AnswerV2 =
            AnswerV2(
                numericAnswer = number, textAnswer = null, mcIdArrayAnswer = null,
                dateAnswer = null, comment = comment
            )

        fun createTextAnswer(text: String, comment: String? = null): AnswerV2 =
            AnswerV2(
                numericAnswer = null, textAnswer = text, mcIdArrayAnswer = null,
                dateAnswer = null, comment = comment
            )

        fun createMcAnswer(mcIdArray: List<Int>, comment: String? = null): AnswerV2 =
            AnswerV2(
                numericAnswer = null, textAnswer = null, mcIdArrayAnswer = mcIdArray,
                dateAnswer = null, comment = comment
            )

        fun createDateAnswer(date: String, comment: String? = null): AnswerV2 =
            AnswerV2(
                numericAnswer = null, textAnswer = null, mcIdArrayAnswer = null,
                dateAnswer = date, comment = comment
            )
    }
}

data class VisibleConditionV2(
    @SerializedName("questionIndex") val questionIndex: Int,
    @SerializedName("relation") val relation: QRelationalEnum,
    @SerializedName("answers") val answers: AnswerV2,
) : Serializable

/** Replaces V1's McOption int mcId with a stable stringId. */
data class MCOptionV2(
    @SerializedName("stringId") val stringId: String?,
    @SerializedName("translations") val translations: MultiLangText,
) : Serializable

/** Represents a question in the form template. */
data class FormTemplateQuestionV2(
    @SerializedName("id") val id: String?,
    @SerializedName("formTemplateId") val formTemplateId: String?,
    @SerializedName("questionType") val questionType: QuestionTypeEnum,
    @SerializedName("order") val order: Int,
    @SerializedName("questionText") val questionText: MultiLangText,
    @SerializedName("questionStringId") val questionStringId: String?,
    @SerializedName("categoryIndex") val categoryIndex: Int?,
    @SerializedName("required") val required: Boolean,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean? = false,
    @SerializedName("allowFutureDates") val allowFutureDates: Boolean?,
    @SerializedName("allowPastDates") val allowPastDates: Boolean?,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleConditionV2>? = emptyList(),
    @SerializedName("stringMaxLength") val stringMaxLength: Int?,
    @SerializedName("stringMaxLines") val stringMaxLines: Int?,
    @SerializedName("numMin") val numMin: Double?,
    @SerializedName("numMax") val numMax: Double?,
    @SerializedName("units") val units: String?,
    @SerializedName("userQuestionId") val userQuestionId: String?,
    @SerializedName("mcOptions") val mcOptions: List<MCOptionV2>?,
) : Serializable

/** Represents a classification for forms. */
data class FormClassificationV2(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: MultiLangText,
    @SerializedName("nameStringId") val nameStringId: String?,
) : Serializable

/** Full template with questions, as returned by GET /forms/v2/templates/{id}
 * todo need to verify if archived works for this endpoint.
 */
data class FormTemplateV2(
    @SerializedName("id") val id: String,
    @SerializedName("version") val version: Int,
    @SerializedName("archived") val archived: Boolean? = false,
    @SerializedName("classification") val classification: FormClassificationV2,
    @SerializedName("dateCreated") val dateCreated: Long,
    @SerializedName("questions") val questions: List<FormTemplateQuestionV2>? = emptyList(),
) : Serializable

/** returned by GET /forms/v2/templates (list). */
data class FormTemplateShallowV2(
    @SerializedName("id") val id: String,
    @SerializedName("formClassificationId") val formClassificationId: String,
    @SerializedName("version") val version: Int,
    @SerializedName("archived") val archived: Boolean,
    @SerializedName("name") val name: String,
    @SerializedName("dateCreated") val dateCreated: Long,
) : Serializable

data class FormTemplateListV2Response(
    @SerializedName("templates") val templates: List<FormTemplateShallowV2>
) : Serializable

data class FormClassificationListV2(
    @SerializedName("classifications") val classifications: List<FormClassificationV2>
) : Serializable

data class FormAnswerV2(
    @Expose @SerializedName("id") val id: String? = null,
    @Expose @SerializedName("questionId") val questionId: String,
    @Expose @SerializedName("formSubmissionId") val formSubmissionId: String? = null,
    @Expose @SerializedName("answer") val answer: AnswerV2,
) : Serializable

data class CreateFormSubmissionRequestV2(
    @Expose @SerializedName("id") val id: String? = null,
    @Expose @SerializedName("formTemplateId") val formTemplateId: String,
    @Expose @SerializedName("patientId") val patientId: String,
    @Expose @SerializedName("userId") val userId: Int? = null,
    @Expose @SerializedName("lang") val lang: String = "English",
    @Expose @SerializedName("answers") val answers: List<FormAnswerV2>,
) : Serializable

data class UpdateFormRequestBodyV2(
    @Expose @SerializedName("answers") val answers: List<FormAnswerV2>
) : Serializable

data class FormSubmissionV2(
    @SerializedName("id") val id: String,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("patientId") val patientId: String,
    @SerializedName("userId") val userId: Int,
    @SerializedName("dateSubmitted") val dateSubmitted: Long,
    @SerializedName("lastEdited") val lastEdited: Long,
    @SerializedName("lang") val lang: String,
    @SerializedName("archived") val archived: Boolean,
) : Serializable

data class AnswerWithQuestionV2(
    @SerializedName("id") val id: String?,
    @SerializedName("questionId") val questionId: String,
    @SerializedName("formSubmissionId") val formSubmissionId: String?,
    @SerializedName("answer") val answer: AnswerV2,
    @SerializedName("questionType") val questionType: QuestionTypeEnum,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("mcOptions") val mcOptions: List<String>,
    @SerializedName("order") val order: Int,
) : Serializable

data class FormSubmissionWithAnswersV2(
    @SerializedName("id") val id: String?,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("patientId") val patientId: String,
    @SerializedName("userId") val userId: Int,
    @SerializedName("dateSubmitted") val dateSubmitted: Long?,
    @SerializedName("lastEdited") val lastEdited: Long?,
    @SerializedName("lang") val lang: String,
    @SerializedName("archived") val archived: Boolean,
    @SerializedName("answers") val answers: List<AnswerWithQuestionV2>?,
) : Serializable
