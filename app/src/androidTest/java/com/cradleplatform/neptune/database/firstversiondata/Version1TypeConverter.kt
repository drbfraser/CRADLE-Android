package com.cradleplatform.neptune.database.firstversiondata

import androidx.room.TypeConverter
import com.cradleplatform.neptune.database.DatabaseTypeConverters
import com.cradleplatform.neptune.database.firstversiondata.model.Assessment
import com.cradleplatform.neptune.database.firstversiondata.model.BloodPressure
import com.cradleplatform.neptune.database.firstversiondata.model.GestationalAge
import com.cradleplatform.neptune.database.firstversiondata.model.Referral
import com.cradleplatform.neptune.database.firstversiondata.model.Sex
import com.cradleplatform.neptune.database.firstversiondata.model.UrineTest
import com.cradleplatform.neptune.model.Answer
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.QuestionResponse
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A list of [TypeConverter] to save objects into room database
 *
 * This must be used in case there are any changes to the original [DatabaseTypeConverters]
 * class in the future. These are the type converters that were compatible with the
 * version 1 schema. In a sense, these are time-frozen type converters.
 *
 * DO NOT EDIT
 */
internal class Version1TypeConverter {
    private inline fun <reified T> readStringValueByJackson(string: String?): T? =
        string?.let { JacksonMapper.mapper.readValue<T>(it) }

    private inline fun <reified T> writeStringByJackson(instance: T?): String? =
        instance?.let { JacksonMapper.mapper.writeValueAsString(it) }

    @TypeConverter
    fun gestationalAgeToString(gestationalAge: GestationalAge?): String? = writeStringByJackson(gestationalAge)

    @TypeConverter
    fun stringToGestationalAge(string: String?): GestationalAge? = readStringValueByJackson(string)

    @TypeConverter
    fun stringToSex(string: String): Sex = enumValueOf(string)

    @TypeConverter
    fun sexToString(sex: Sex): String = sex.name

    @TypeConverter
    fun fromStringList(list: List<String>?): String? = writeStringByJackson(list)

    @TypeConverter
    fun toStringList(string: String?): List<String>? = readStringValueByJackson(string)

    @TypeConverter
    fun toBloodPressure(string: String?): BloodPressure? = readStringValueByJackson(string)

    @TypeConverter
    fun fromBloodPressure(bloodPressure: BloodPressure?): String? = writeStringByJackson(bloodPressure)

    @TypeConverter
    fun toUrineTest(string: String?): UrineTest? = readStringValueByJackson(string)

    @TypeConverter
    fun fromUrineTest(urineTest: UrineTest?): String? = writeStringByJackson(urineTest)

    @TypeConverter
    fun toReferral(string: String?): Referral? = readStringValueByJackson(string)

    @TypeConverter
    fun fromReferral(referral: Referral?): String? = writeStringByJackson(referral)

    @TypeConverter
    fun toFollowUp(string: String?): Assessment? = readStringValueByJackson(string)

    @TypeConverter
    fun fromFollowUp(assessment: Assessment?): String? = writeStringByJackson(assessment)

    @TypeConverter
    fun fromFormTemplate(formTemplate: FormTemplate?): String? =
        formTemplate?.let { Gson().toJson(formTemplate) }

    @TypeConverter
    fun toFormTemplate(string: String?): FormTemplate? =
        string?.let { Gson().fromJson(string, FormTemplate::class.java) }

    @TypeConverter
    fun fromFormResponseAnswers(answers: Map<String, Answer>?): String? =
        answers?.let { Gson().toJson(answers) }

    @TypeConverter
    fun toFormResponseAnswers(string: String?): Map<String, Answer>? =
        string?.let { Gson().fromJson(string, object : TypeToken<Map<String, Answer>>() {}.type) }
    @TypeConverter
    fun fromQuestionResponseList(list: List<QuestionResponse>?): String? =
        list?.let { Gson().toJson(list) }

    @TypeConverter
    fun toQuestionResponseList(string: String?): List<QuestionResponse>? =
        string?.let { Gson().fromJson(string, object : TypeToken<List<QuestionResponse>>() {}.type) }
}
