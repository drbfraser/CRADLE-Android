package com.cradleVSA.neptune.database

import androidx.room.TypeConverter
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.BloodPressure
import com.cradleVSA.neptune.model.GestationalAge
import com.cradleVSA.neptune.model.Referral
import com.cradleVSA.neptune.model.Sex
import com.cradleVSA.neptune.model.UrineTest
import com.cradleVSA.neptune.utilities.jackson.JacksonMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * A list of [TypeConverter] to save objects into room database
 */
class DatabaseTypeConverters {
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
}
