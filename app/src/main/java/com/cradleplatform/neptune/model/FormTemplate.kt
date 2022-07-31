package com.cradleplatform.neptune.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/*
FormTemplate stores the form templated that synced from web.
Can be updated for newer version of templates.
'Answers' class could be any type of user input, just add String type for now.
Currently using Gson, could use another library if needed.
*/

data class FormTemplate(

    @SerializedName("version") val version: String,
    @SerializedName("name") val name: String,
    @SerializedName("dateCreated") val dateCreated: Int,
    @SerializedName("category") val category: String,
    @SerializedName("id") val id: String,
    @SerializedName("lastEdited") val lastEdited: Int,
    @SerializedName("lang") val lang: String,
    @SerializedName("questions") val questions: List<Questions>
) : Serializable

data class Questions(

    @SerializedName("id") val id: String,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>,
    @SerializedName("isBlank") val isBlank: Boolean,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("mcOptions") val mcOptions: List<McOptions>,
    @SerializedName("questionIndex") val questionIndex: Int,
    @SerializedName("numMin") val numMin: Double,
    @SerializedName("numMax") val numMax: Double,
    @SerializedName("questionId") val questionId: String,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("questionType") val questionType: String,
    @SerializedName("answers") var answers: Answers,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean,
    @SerializedName("required") val required: Boolean
) : Serializable

data class McOptions(

    @SerializedName("mcid") val mcid: Int,
    @SerializedName("opt") val opt: String
) : Serializable

data class VisibleCondition(

    @SerializedName("qidx") val qidx: Int,
    @SerializedName("relation") val relation: String,
    @SerializedName("answers") var answers: Answers
) : Serializable

data class Answers(

    @SerializedName("answers") var answers: String?,
) : Serializable
