package com.cradle.neptune.database

import androidx.room.TypeConverter
import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.FollowUp
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.ReadingMetadata
import com.cradle.neptune.model.Referral
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import com.google.gson.Gson
import com.google.gson.JsonArray

/**
 * A list of [TypeConverter] to save objects into room database
 */
class DatabaseTypeConverters {

    @TypeConverter
    fun gestationalAgeToString(gestationalAge: GestationalAge?): String? =
        Gson().toJson(gestationalAge)

    @TypeConverter
    fun stringToGestationalAge(string: String?): GestationalAge? =
        Gson().fromJson(string, GestationalAge::class.java)

    @TypeConverter
    fun stringToSex(string: String): Sex = enumValueOf(string)

    @TypeConverter
    fun sexToString(sex: Sex): String = sex.name

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        val jsonArray = JsonArray()
        list.forEach {
            jsonArray.add(it)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(string: String): List<String> =
        Gson().fromJson(string, mutableListOf<String>().javaClass)

    @TypeConverter
    fun toBloodPressure(string: String): BloodPressure =
        Gson().fromJson(string,BloodPressure::class.java)

    @TypeConverter
    fun fromBloodPressure(bloodPressure: BloodPressure):String =
        Gson().toJson(bloodPressure)

    @TypeConverter
    fun toUrineTest(string: String): UrineTest? =
        Gson().fromJson(string,UrineTest::class.java)

    @TypeConverter
    fun fromUrineTest(urineTest: UrineTest?):String =
        Gson().toJson(urineTest)

    @TypeConverter
    fun toReferral(string: String): Referral? =
        Gson().fromJson(string,Referral::class.java)

    @TypeConverter
    fun FromReferral(referral: Referral?):String =
        Gson().toJson(referral)

    @TypeConverter
    fun toFollowUp(string: String): FollowUp? =
        Gson().fromJson(string,FollowUp::class.java)

    @TypeConverter
    fun FromFollowUp(followUp: FollowUp?):String =
        Gson().toJson(followUp)

    @TypeConverter
    fun toReadingMetaData(string: String): ReadingMetadata? =
        Gson().fromJson(string,ReadingMetadata::class.java)

    @TypeConverter
    fun fromUrineTest(readingMetadata: ReadingMetadata?):String =
        Gson().toJson(readingMetadata)
}
