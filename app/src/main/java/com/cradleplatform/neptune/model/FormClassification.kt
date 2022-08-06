package com.cradleplatform.neptune.model

import androidx.room.Entity
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Holds the latest version of [FormTemplate] for a [FormClassification]
 *
 * only hold one version(the newest) of a [FormTemplate]. They are stored as a Json String
 * in the table "FormClassification" with [formClassId] as primary key
 *
 * @property formClassId The unique ID for the form classification (auto-generated in the backend)
 * @property formClassName The name for the classification for the set of different versions for same form
 * @property formTemplate The Json String for a formTemplate, stores the latest version got from server
 */
@Entity(
    indices = [],
    primaryKeys = ["formClassId"]
)
class FormClassification(
    var formClassId: String,

    var formClassName: String,

    var formTemplate: FormTemplate
) {

    /**
     * A custom Gson Deserializer for Interpreting incoming
     * backend FormTemplate(deep copy with Classification)
     * into Android FormClassification object
     */
    class DeserializerFromFormTemplateStream : JsonDeserializer<FormClassification> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): FormClassification {

            val formTemplate = Gson().fromJson(json, FormTemplate::class.java)

            var className: String
            var classId: String
            json!!.asJsonObject.let { rootObject ->
                rootObject!!.getAsJsonObject("classification").let {
                    classification ->
                    className = classification.get("name").asString
                    classId = classification.get("id").asString
                }
            }

            return FormClassification(classId, className, formTemplate)
        }
    }
}
