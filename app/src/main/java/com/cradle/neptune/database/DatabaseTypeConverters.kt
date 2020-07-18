package com.cradle.neptune.database

import androidx.room.TypeConverter
import com.cradle.neptune.model.GestationalAge
import com.cradle.neptune.model.Sex
import com.google.gson.Gson
import com.google.gson.JsonArray

/**
 * A list of [TypeConverter] to save objects into room database
 */
class DatabaseTypeConverters {

    @TypeConverter
    fun gestationalAgeToString(gestationalAge: GestationalAge?):String? = Gson().toJson(gestationalAge)

    @TypeConverter
    fun stringToGestationalAge(string: String?):GestationalAge? = Gson().fromJson(string,GestationalAge::class.java)

    @TypeConverter
    fun stringToSex(string: String):Sex = enumValueOf(string)

    @TypeConverter
    fun sexToString(sex: Sex):String = sex.name

    @TypeConverter
    fun fromList(list: List<String>): String {
       val jsonArray:JsonArray = JsonArray()
       list.forEach{
          jsonArray.add(it)
       }
       return jsonArray.toString()
    }

   @TypeConverter
   fun toList(string: String):List<String> = Gson().fromJson(string, mutableListOf<String>().javaClass)

}