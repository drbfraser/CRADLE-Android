package com.cradle.neptune.binding

import android.content.Context
import androidx.databinding.InverseMethod
import com.cradle.neptune.R
import com.cradle.neptune.model.Sex
import java.lang.NumberFormatException

object BindingConverter {
    @InverseMethod("stringToInt")
    @JvmStatic fun intToString(value: Int?): String? {
        return value?.toString()
    }

    @JvmStatic fun stringToInt(value: String?): Int? {
        return try {
            value?.toInt()
        } catch (e: NumberFormatException) {
            null
        }
    }

    @InverseMethod("stringToSex")
    @JvmStatic fun sexToString(context: Context, value: Sex?): String? = when (value) {
        Sex.MALE -> context.resources.getStringArray(R.array.sex)[0]
        Sex.FEMALE -> context.resources.getStringArray(R.array.sex)[1]
        Sex.OTHER -> context.resources.getStringArray(R.array.sex)[2]
        else -> null
    }

    @JvmStatic fun stringToSex(context: Context, value: String?): Sex? = when (value) {
        context.resources.getStringArray(R.array.sex)[0] -> Sex.MALE
        context.resources.getStringArray(R.array.sex)[1] -> Sex.FEMALE
        context.resources.getStringArray(R.array.sex)[2] -> Sex.OTHER
        else -> null
    }
}
