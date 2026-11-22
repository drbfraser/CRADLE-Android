package com.cradleplatform.neptune.database

import androidx.room.TypeConverter
import com.cradleplatform.neptune.model.AnswerWithQuestionV2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FormSubmissionV2TypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun submissionAnswersV2ToJson(answers: List<AnswerWithQuestionV2>): String = gson.toJson(answers)

    @TypeConverter
    fun jsonToSubmissionAnswersV2(json: String): List<AnswerWithQuestionV2> =
        requireNotNull(gson.fromJson<List<AnswerWithQuestionV2>>(json, answersType)) {
            "Missing V2 submission answers"
        }

    companion object {
        private val answersType = object : TypeToken<List<AnswerWithQuestionV2>>() {}.type
    }
}
