package com.cradleplatform.neptune.model

import com.google.gson.annotations.SerializedName

data class FormTemplate(

    @SerializedName("id") val id : String?,
    @SerializedName("name") val name : String?,
    @SerializedName("category") val category : String?,
    @SerializedName("version") val version : String?,
    @SerializedName("questions") val questions: List<Questions>?
)

data class Questions(

    @SerializedName("questionId") val questionId : String?,
    @SerializedName("categoryIndex") val categoryIndex : String?,
    @SerializedName("questionIndex") val questionIndex : Int?,
    @SerializedName("questionType") val questionType : String?,
    @SerializedName("required") val required : Boolean?,
    @SerializedName("numMin") val numMin : Double?,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>?,
    @SerializedName("questionLangVersions") val questionLangVersions: List<QuestionLangVersions>?
)

data class VisibleCondition(

    @SerializedName("qidx") val qidx : Int?,
    @SerializedName("relation") val relation : String?,
    @SerializedName("answers") val answers : Answers?
)

data class Answers(

    @SerializedName("number") val number : Int?
)

data class QuestionLangVersions(

    @SerializedName("lang") val lang : String?,
    @SerializedName("questionText") val questionText : String?,
    @SerializedName("mcOptions") val mcOptions: List<McOptions>?
)

data class McOptions(

    @SerializedName("mcid") val mcid : Int?,
    @SerializedName("opt") val opt : String?
)
