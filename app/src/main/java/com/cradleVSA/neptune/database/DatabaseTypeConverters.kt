package com.cradleVSA.neptune.database

import androidx.room.TypeConverter
import com.cradleVSA.neptune.model.Assessment
import com.cradleVSA.neptune.model.BloodPressure
import com.cradleVSA.neptune.model.GestationalAge
import com.cradleVSA.neptune.model.ReadingMetadata
import com.cradleVSA.neptune.model.Referral
import com.cradleVSA.neptune.model.Sex
import com.cradleVSA.neptune.model.UrineTest
import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.json.JSONObject

/**
 * A list of [TypeConverter] to save objects into room database
 */
class DatabaseTypeConverters {

    @TypeConverter
    fun gestationalAgeToString(gestationalAge: GestationalAge?): String? =
        JacksonMapper.writerForGestAge.writeValueAsString(gestationalAge)

    @TypeConverter
    fun stringToGestationalAge(string: String?): GestationalAge? =
        string?.let {
            if (it == "null") {
                null
            } else {
                JacksonMapper.readerForGestAge.readValue<GestationalAge>(it)
            }
        }

    @TypeConverter
    fun stringToSex(string: String): Sex = enumValueOf(string)

    @TypeConverter
    fun sexToString(sex: Sex): String = sex.name

    @TypeConverter
    fun fromStringList(list: List<String>?): String? =
        list?.let { JacksonMapper.mapper.writeValueAsString(it) }

    @TypeConverter
    fun toStringList(string: String?): List<String>? = string?.let {
        JacksonMapper.mapper.readValue<List<String>>(it)
    }

    @TypeConverter
    fun toBloodPressure(string: String?): BloodPressure? =
        string?.let { if (it == "null") null else BloodPressure.unmarshal(JSONObject(string)) }

    @TypeConverter
    fun fromBloodPressure(bloodPressure: BloodPressure?): String? =
        bloodPressure?.marshal()?.toString()

    @TypeConverter
    fun toUrineTest(string: String?): UrineTest? =
        string?.let { if (it == "null") null else UrineTest.unmarshal(JSONObject(it)) }

    @TypeConverter
    fun fromUrineTest(urineTest: UrineTest?): String? = urineTest?.marshal()?.toString()

    @TypeConverter
    fun toReferral(string: String?): Referral? =
        string?.let { if (it == "null") null else Referral.unmarshal(JSONObject(it)) }

    @TypeConverter
    fun fromReferral(referral: Referral?): String? = referral?.marshal()?.toString()

    @TypeConverter
    fun toFollowUp(string: String?): Assessment? =
        string?.let { if (it == "null") null else Assessment.unmarshal(JSONObject(it)) }

    @TypeConverter
    fun fromFollowUp(followUp: Assessment?): String? = followUp?.marshal()?.toString()

    @TypeConverter
    fun toReadingMetadata(string: String?): ReadingMetadata? =
        string?.let { if (it == "null") null else ReadingMetadata.unmarshal(JSONObject(it)) }

    @TypeConverter
    fun fromReadingMetaData(readingMetadata: ReadingMetadata?): String? =
        readingMetadata?.marshal()?.toString()
}
