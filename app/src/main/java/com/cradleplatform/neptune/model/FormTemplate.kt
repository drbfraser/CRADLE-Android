package com.cradleplatform.neptune.model

import android.util.Log
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 *  [FormTemplate] stores the form templated that synced from backend.
 *  Dynamically implemented so that it can be updated for newer version of templates.
 *  'Answers' class could be any type of user input, just add String type for now.
 *  Currently using Gson, could use another library if needed.
 *
 *  !!Field Nullability!!
 *
 *  As it is possible that Gson bypasses non-null check when paring,
 *  (reference: https://stackoverflow.com/questions/71923931/null-safety-issues-with-gson-in-kotlin)
 *  every auto-parsed field is nullable, so that user requires to perform null-safety check
 */
data class FormTemplate(

    // version (assigned by system admin to loosely track version. May be "Jan 2022", or "V1", ...)
    @SerializedName("version") val version: String?,
    @SerializedName("archived") val archived: Boolean?,
    // timestamp (filled by server when created)
    @SerializedName("dateCreated") val dateCreated: Long?,
    @SerializedName("id") val id: String?,
    @SerializedName("formClassificationId") val formClassId: String?,
    @SerializedName("formClassificationName") var formClassName: String?,
    @SerializedName("questions") val questions: List<Question>?,

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

    /**
     *  Performs a Null-check for every inner fields in the [FormTemplate] that
     *  should not be null except backend-nullable fields. This is to verify if
     *  class was parsed successfully
     *
     *  @return true if integrity verified
     */
    fun verifyIntegrity(): Boolean {

        var nullCheckResult = true

        this@FormTemplate.version ?: let {
            nullCheckResult = false
            Log.e(TAG, "[version] was null")
        }
        this@FormTemplate.archived ?: let {
            nullCheckResult = false
            Log.e(TAG, "[archived] was null")
        }
        this@FormTemplate.dateCreated ?: let {
            nullCheckResult = false
            Log.e(TAG, "[dateCreated] was null")
        }
        this@FormTemplate.id ?: let {
            nullCheckResult = false
            Log.e(TAG, "[id] was null")
        }
        this@FormTemplate.formClassId ?: let {
            nullCheckResult = false
            Log.e(TAG, "[formClassId] was null")
        }
        this@FormTemplate.questions?.forEach { it.verifyIntegrity() }
            ?: let {
                nullCheckResult = false
                Log.w(Question.TAG, "[questions] was null")
            }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormTemplate"
    }
}

enum class QuestionTypeEnum {
    INTEGER,
    DECIMAL,
    STRING,
    MULTIPLE_CHOICE,
    MULTIPLE_SELECT,
    DATE,
    TIME,
    DATETIME,
    CATEGORY,
}

data class Question(
    @SerializedName("id") var id: String?,
    @SerializedName("allowPastDates") val allowPastDates: Boolean?,
    @SerializedName("allowFutureDates") val allowFutureDates: Boolean?,
    @SerializedName("visibleCondition") val visibleCondition: List<VisibleCondition>?,
    @SerializedName("isBlank") val isBlank: Boolean?, // Should be true for FormTemplates
    @SerializedName("formTemplateId") val formTemplateId: String?, // Backend-Nullable
    @SerializedName("questionIndex") val questionIndex: Int?,
    @SerializedName("numMin") val numMin: Double?, // Backend-Nullable
    @SerializedName("numMax") val numMax: Double?, // Backend-Nullable
    @SerializedName("stringMaxLength") val stringMaxLength: Int?, // Backend-Nullable
    @SerializedName("stringMaxLines") val stringMaxLines: Int?, // Backend-Nullable
    @SerializedName("questionType") val questionType: QuestionTypeEnum?,
    @SerializedName("hasCommentAttached") val hasCommentAttached: Boolean?,
    @SerializedName("required") val required: Boolean?,
    @SerializedName("langVersions") val languageVersions: List<QuestionLangVersion>?
) : Serializable {

    override fun toString(): String {
        return "Question(questionId=$id, " +
            "allowPastDates=$allowPastDates, " +
            "allowFutureDates=$allowFutureDates, " +
            "visibleCondition=$visibleCondition, " +
            "isBlank=$isBlank, " +
            "formTemplateId=$formTemplateId," +
            "questionIndex=$questionIndex, " +
            "numMin=$numMin, " +
            "numMax=$numMax, " +
            "stringMaxLength=$stringMaxLength, " +
            "stringMaxLines=$stringMaxLines, " +
            "questionType=$questionType, " +
            "hasCommentAttached=$hasCommentAttached, " +
            "required=$required, " +
            "languageVersions=$languageVersions)"
    }

    /**
     * Checks if fields has been parsed successfully, where fields should not be null except:
     * backend-nullable fields like formTemplateId, numMin, numMax, stringMaxLength, stringMaxLines
     */
    fun verifyIntegrity(): Boolean {
        var nullCheckResult = true

        this@Question.id ?: let {
            nullCheckResult = false
            Log.w(TAG, "[questionId] was null")
        }
        this@Question.isBlank ?: let {
            nullCheckResult = false
            Log.w(TAG, "[isBlank] was null")
        }

        this@Question.questionIndex ?: let {
            nullCheckResult = false
            Log.w(TAG, "[questionIndex] was null")
        }

        this@Question.questionType ?: let {
            nullCheckResult = false
            Log.w(TAG, "[questionType] was null")
        }

        this@Question.hasCommentAttached ?: let {
            nullCheckResult = false
            Log.w(TAG, "[hasCommentAttached] was null")
        }

        this@Question.required ?: let {
            nullCheckResult = false
            Log.w(TAG, "[required] was null")
        }

        this@Question.languageVersions?.forEach { it.verifyIntegrity() }
            ?: let {
                nullCheckResult = false
                Log.w(TAG, "[languageVersions] was null")
            }

        this@Question.visibleCondition?.forEach { it.verifyIntegrity() }
            ?: let {
                nullCheckResult = false
                Log.w(TAG, "[visibleCondition] was null")
            }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormQuestions"
    }
}

data class QuestionLangVersion(
    @SerializedName("lang") val language: String?,
    @SerializedName("questionId") val parentId: String?,
    @SerializedName("questionText") val questionText: String?,
    @SerializedName("id") val questionTextId: Int?,
    @SerializedName("mcOptions") val mcOptions: List<McOption>?
) : Serializable {

    override fun toString(): String {
        return "QuestionLangVersion(language=$language, " +
            "parentId=$parentId, " +
            "questionText=$questionText, " +
            "questionTextId=$questionTextId, " +
            "mcOptions=$mcOptions)"
    }

    fun verifyIntegrity(): Boolean {
        var nullCheckResult = true

        this@QuestionLangVersion.language ?: let {
            nullCheckResult = false
            Log.w(TAG, "[language] was null")
        }
        this@QuestionLangVersion.parentId ?: let {
            nullCheckResult = false
            Log.w(TAG, "[parentId] was null")
        }
        this@QuestionLangVersion.questionText ?: let {
            nullCheckResult = false
            Log.w(TAG, "[questionText] was null")
        }
        this@QuestionLangVersion.language ?: let {
            nullCheckResult = false
            Log.w(TAG, "[language] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormQuestionLangVersion"
    }
}

data class McOption(
    @Expose @SerializedName("mcId") val mcId: Int?,
    @Expose @SerializedName("opt") val opt: String?
) : Serializable {

    fun verifyIntegrity(): Boolean {
        var nullCheckResult = true

        this@McOption.mcId ?: let {
            nullCheckResult = false
            Log.w(TAG, "[mcId] was null")
        }
        this@McOption.opt ?: let {
            nullCheckResult = false
            Log.w(TAG, "[opt] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormMcOptions"
    }
}

data class VisibleCondition(
    @Expose @SerializedName("questionIndex") val questionIndex: Int?,
    @Expose @SerializedName("relation") val relation: String?,
    @Expose @SerializedName("answers") var answerCondition: Answer?
) : Serializable {

    fun verifyIntegrity(): Boolean {
        var nullCheckResult = true

        this@VisibleCondition.questionIndex ?: let {
            nullCheckResult = false
            Log.w(TAG, "[questionIndex] was null")
        }
        this@VisibleCondition.relation ?: let {
            nullCheckResult = false
            Log.w(TAG, "[relation] was null")
        }

        this@VisibleCondition.answerCondition ?: let {
            nullCheckResult = false
            Log.w(TAG, "[answers] was null")
        }

        return nullCheckResult
    }

    companion object {
        const val TAG = "FormVisibleCondition"
    }
}

/**
 *  Represents a JSON style [Answer] object (different field names for different types of answer)
 *
 *  Answer could be any one of the following:
 *
 *  1) "number", numeric values, can be any subclass of abstract class [Number], eg. Long, Double
 *      { "number": 123.4 }
 *  2) "text", Textual input, potentially including date-time object
 *      { "text": "Hello world 123! :)" }
 *  3) "mcidArray", Array of selected mc choice(s),eg [0,3] for choice 1 and 4, with 2 and 3 not selected
 *      { "mcidArray": [0, 1] }
 *
 *  only one of the above field can be non-null, so that when serialized,
 *    only one type (or with comment) is in the Json object
 *
 */
data class Answer private constructor(
    @Expose @SerializedName("number") val numericAnswer: Number?,
    @Expose @SerializedName("text") val textAnswer: String?,
    @Expose @SerializedName("mcIdArray") val mcIdArrayAnswer: List<Int>?,
    @Expose @SerializedName("comment") val comment: String?,
) : Serializable {

    fun isValidAnswer(): Boolean {
        if (numericAnswer != null) return true
        if (mcIdArrayAnswer != null && mcIdArrayAnswer.isNotEmpty()) return true
        return textAnswer?.isNotEmpty() == true
    }

    fun hasComment(): Boolean = !(comment?.isEmpty() ?: true)

    companion object {
        private val TAG = "FormAnswer"

        fun createNumericAnswer(numericString: Number, comment: String = ""): Answer {
            return Answer(
                numericAnswer = numericString,
                null,
                null,
                comment
            )
        }

        fun createTextAnswer(textString: String, comment: String = ""): Answer {
            return Answer(
                null,
                textAnswer = textString,
                null,
                comment
            )
        }

        fun createMcAnswer(mcidArray: List<Int>, comment: String = ""): Answer {
            return Answer(
                null,
                null,
                mcIdArrayAnswer = mcidArray,
                comment
            )
        }

        fun createEmptyAnswer(comment: String = ""): Answer {
            return Answer(
                null,
                null,
                mcIdArrayAnswer = null,
                comment
            )
        }
    }
}
