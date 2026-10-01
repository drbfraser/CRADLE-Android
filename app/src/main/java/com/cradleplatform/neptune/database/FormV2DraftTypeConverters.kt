package com.cradleplatform.neptune.database

import androidx.room.TypeConverter
import com.cradleplatform.neptune.model.FormAnswerV2
import com.cradleplatform.neptune.model.FormTemplateV2
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.reflect.TypeToken

/**
 * Room converters for the JSON fields stored by [com.cradleplatform.neptune.model.FormV2Draft].
 *
 * These are separate from [DatabaseTypeConverters] so V2 numeric answers can retain the concrete
 * types produced by the renderer without changing serialization behavior for existing V1 models.
 */
class FormV2DraftTypeConverters {
    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Number::class.java, JsonDeserializer<Number> { json, _, _ ->
            // Match the types produced by FormV2RenderingActivity: INTEGER is Long, DECIMAL is Double.
            val value = json.asString
            value.toLongOrNull() ?: value.toDouble()
        })
        .create()

    @TypeConverter
    fun formTemplateV2ToJson(formTemplate: FormTemplateV2): String = gson.toJson(formTemplate)

    @TypeConverter
    fun jsonToFormTemplateV2(json: String): FormTemplateV2 =
        requireNotNull(gson.fromJson(json, FormTemplateV2::class.java)) {
            "Missing V2 draft template"
        }

    @TypeConverter
    fun formAnswersV2ToJson(formAnswers: List<FormAnswerV2>): String = gson.toJson(formAnswers)

    @TypeConverter
    fun jsonToFormAnswersV2(json: String): List<FormAnswerV2> =
        requireNotNull(gson.fromJson<List<FormAnswerV2>>(json, formAnswersV2Type)) {
            "Missing V2 draft answers"
        }

    companion object {
        private val formAnswersV2Type = object : TypeToken<List<FormAnswerV2>>() {}.type
    }
}
