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
    @SerializedName("archived") val archived: Boolean,
    @SerializedName("dateCreated") val dateCreated: Int,
    @SerializedName("id") val id: String,
    @SerializedName("formClassificationId") val formClassId: String,
    @SerializedName("questions") val questions: List<Questions>,

) : Serializable {

    fun languageVersions(): List<String> {
        val languageVersions = mutableListOf<String>()

        val languageHash: HashMap<String, Int> = HashMap()

        for (question in questions) {
            question.languageVersions.forEach { questionVersions ->
                val currentCount = languageHash[questionVersions.language]
                languageHash[questionVersions.language] = (currentCount?.let { it + 1 }) ?: 1
            }
        }

        var maxCount = 0
        languageHash.forEach { (language, count) ->
            if (count > maxCount) {
                maxCount = count
                languageVersions.clear()
            }
            languageVersions.add(language)
        }

        return languageVersions
    }
}

data class Questions(
    @SerializedName("id") val id: String,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>,
    @SerializedName("isBlank") val isBlank: Boolean,
    @SerializedName("formTemplateId") val formTemplateId: String,
    @SerializedName("mcOptions") val mcOptions: List<McOptions>,
    @SerializedName("questionIndex") val questionIndex: Int,
    @SerializedName("numMin") val numMin: Double?,
    @SerializedName("questionId") val questionId: String,
    //val questionText: String,
    @SerializedName("questionType") val questionType: String,
    @SerializedName("answers") val answers: Answers?,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean,
    @SerializedName("required") val required: Boolean,
    @SerializedName("questionLangVersions") val languageVersions: List<QuestionLangVersion>
) : Serializable

data class QuestionLangVersion(
    @SerializedName("lang") val language: String,
    @SerializedName("qid") val parentId: String,
    @SerializedName("questionText") val questionText: String,
    @SerializedName("id") val questionTextId: Int,
) : Serializable

data class McOptions(

    @SerializedName("mcid") val mcid: Int,
    @SerializedName("opt") val opt: String
) : Serializable

data class VisibleCondition(

    @SerializedName("qidx") val qidx: Int,
    @SerializedName("relation") val relation: String,
    @SerializedName("answers") val answers: Answers
) : Serializable

data class Answers(

    @SerializedName("answers") val answers: String?,
) : Serializable
