package com.cradleplatform.neptune.model

import android.util.Log
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 *  [FormTemplate] stores the form templated that synced from backend.
 *  Dynamically implemented so that it can be updated for newer version of templates.
 *  'Answers' class could be any type of user input, just add String type for now.
 *  Currently using Gson, could use another library if needed.
 *
 *  Field Nullability
 *
 *  As it is possible that Gson bypasses non-null check when paring,
 *  (reference: https://stackoverflow.com/questions/71923931/null-safety-issues-with-gson-in-kotlin)
 *  every auto-parsed field is nullable, so that user requires to perform null-safety check
 *
 */
data class FormTemplate(

    @SerializedName("version") val version: String?,
    @SerializedName("archived") val archived: Boolean?,
    @SerializedName("dateCreated") val dateCreated: Int?,
    @SerializedName("id") val id: String?,
    @SerializedName("formClassificationId") val formClassId: String?,
    @SerializedName("questions") val questions: List<Questions>?,

) : Serializable {

    fun languageVersions(): List<String> {
        val languageVersions = mutableListOf<String>()

        val languageHash: HashMap<String, Int> = HashMap()

        questions?.let {
            // if there is questions (not null)
            for (question in questions) {
                // languageVersions should never be null unless invalid form or parse error
                question.languageVersions!!.forEach { questionVersions ->
                    val currentCount = languageHash[questionVersions.language!!]
                    languageHash[questionVersions.language] = (currentCount?.let { it + 1 }) ?: 1
                }
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

    fun deepNullCheck(): Boolean {

        var nullCheckResult = true

        //nullCheckResult = nullCheckResult && this@FormTemplate.version != null
        this@FormTemplate.version ?: let {
            nullCheckResult = false
            Log.e(TAG,"[version] was null")
        }
        this@FormTemplate.archived ?: let {
            nullCheckResult = false
            Log.e(TAG,"[archived] was null")
        }
        this@FormTemplate.dateCreated ?: let {
            nullCheckResult = false
            Log.e(TAG,"[dateCreated] was null")
        }
        this@FormTemplate.id ?: let {
            nullCheckResult = false
            Log.e(TAG,"[id] was null")
        }
        this@FormTemplate.formClassId ?: let {
            nullCheckResult = false
            Log.e(TAG,"[formClassId] was null")
        }

        this@FormTemplate.questions?.forEach { it.deepNullCheck() }
            ?: let {
                nullCheckResult = false
                Log.w(Questions.TAG,"[questions] was null")
            }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormTemplate"
    }
}

data class Questions(

    @SerializedName("id") val id: String?,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>?,
    @SerializedName("isBlank") val isBlank: Boolean?,
    @SerializedName("formTemplateId") val formTemplateId: String?,
    @SerializedName("mcOptions") val mcOptions: List<McOptions>?,
    @SerializedName("questionIndex") val questionIndex: Int?,
    @SerializedName("numMin") val numMin: Double?,
    @SerializedName("numMax") val numMax: Double?,
    @SerializedName("questionId") val questionId: String?,
    @SerializedName("questionType") val questionType: String?,
    @SerializedName("answers") var answers: Answers?,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean?,
    @SerializedName("required") val required: Boolean?,
    @SerializedName("questionLangVersions") val languageVersions: List<QuestionLangVersion>?
) : Serializable {

    fun deepNullCheck(): Boolean {
        var nullCheckResult = true

        this@Questions.id ?: let{
            nullCheckResult = false
            Log.w(TAG,"[id] was null")
        }
        this@Questions.isBlank ?: let{
            nullCheckResult = false
            Log.w(TAG,"[isBlank] was null")
        }

        this@Questions.formTemplateId ?: let{
            nullCheckResult = false
            Log.w(TAG,"[formTemplateId] was null")
        }

        this@Questions.questionIndex ?: let{
            nullCheckResult = false
            Log.w(TAG,"[questionIndex] was null")
        }

        this@Questions.numMax ?: let{
            nullCheckResult = false
            Log.w(TAG,"[numMax] was null")
        }

        this@Questions.numMin ?: let{
            nullCheckResult = false
            Log.w(TAG,"[numMin] was null")
        }

        this@Questions.questionId ?: let{
            nullCheckResult = false
            Log.w(TAG,"[questionId] was null")
        }

        this@Questions.questionType ?: let{
            nullCheckResult = false
            Log.w(TAG,"[questionType] was null")
        }

        this@Questions.answers ?: let{
            nullCheckResult = false
            Log.w(TAG,"[answers] was null")
        }

        this@Questions.hasCommentAttached ?: let{
            nullCheckResult = false
            Log.w(TAG,"[hasCommentAttached] was null")
        }

        this@Questions.required ?: let{
            nullCheckResult = false
            Log.w(TAG,"[required] was null")
        }

        this@Questions.languageVersions?.forEach { it.deepNullCheck() }
            ?: let {
                nullCheckResult = false
                Log.w(TAG,"[languageVersions] was null")
            }


        this@Questions.mcOptions?.forEach { it.deepNullCheck() }
            ?: let {
                nullCheckResult = false
                Log.w(TAG,"[mcOptions] was null")
            }
        this@Questions.visibleCondition?.forEach{ it.deepNullCheck() }
            ?: let {
                nullCheckResult = false
                Log.w(TAG,"[visibleCondition] was null")
            }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormQuestions"
    }
}

data class QuestionLangVersion(
    @SerializedName("lang") val language: String?,
    @SerializedName("qid") val parentId: String?,
    @SerializedName("questionText") val questionText: String?,
    @SerializedName("id") val questionTextId: Int?,
) : Serializable {

    fun deepNullCheck(): Boolean {
        var nullCheckResult = true

        this@QuestionLangVersion.language ?: let{
            nullCheckResult = false
            Log.w(TAG,"[language] was null")
        }
        this@QuestionLangVersion.parentId ?: let{
            nullCheckResult = false
            Log.w(TAG,"[parentId] was null")
        }
        this@QuestionLangVersion.questionText ?: let{
            nullCheckResult = false
            Log.w(TAG,"[questionText] was null")
        }
        this@QuestionLangVersion.language ?: let{
            nullCheckResult = false
            Log.w(TAG,"[language] was null")
        }

        return nullCheckResult
    }
    companion object {
        const val TAG = "FormQuestionLangVersion"
    }
}

data class McOptions(

    @SerializedName("mcid") val mcid: Int?,
    @SerializedName("opt") val opt: String?
) : Serializable {

    fun deepNullCheck(): Boolean {
        var nullCheckResult = true

        this@McOptions.mcid ?: let{
            nullCheckResult = false
            Log.w(TAG,"[mcid] was null")
        }
        this@McOptions.opt ?: let{
            nullCheckResult = false
            Log.w(TAG,"[opt] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormMcOptions"
    }
}

data class VisibleCondition(

    @SerializedName("qidx") val qidx: Int?,
    @SerializedName("relation") val relation: String?,
    @SerializedName("answers") var answers: Answers?
) : Serializable {

    fun deepNullCheck(): Boolean {
        var nullCheckResult = true

        this@VisibleCondition.qidx ?: let{
            nullCheckResult = false
            Log.w(TAG,"[qidx] was null")
        }
        this@VisibleCondition.relation ?: let{
            nullCheckResult = false
            Log.w(TAG,"[relation] was null")
        }
        this@VisibleCondition.answers ?: let{
            nullCheckResult = false
            Log.w(TAG,"[answers] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormVisibleCondition"
    }
}

data class Answers(

    @SerializedName("answers") var answers: String?,
) : Serializable {

    fun deepNullCheck(): Boolean {
        var nullCheckResult = true

        this@Answers.answers ?: let{
            nullCheckResult = false
            Log.w(TAG,"[answers] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormAnswer"
    }
}

