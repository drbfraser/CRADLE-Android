package com.cradle.neptune.binding

import androidx.databinding.InverseMethod
import java.lang.NumberFormatException

object BindingConverter {
    @InverseMethod("stringToInt")
    @JvmStatic
    fun intToString(value: Int?): String? {
        return value?.toString()
    }

    @JvmStatic
    fun stringToInt(value: String?): Int? {
        return try {
            value?.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
