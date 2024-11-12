package com.cradleplatform.neptune.binding

import android.content.Context
import androidx.databinding.InverseMethod
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.Sex
import com.cradleplatform.neptune.view.newPatient.activities.ReadingActivity

object Converter {
    @InverseMethod("stringToInt")
    @JvmStatic
    fun intToString(value: Int?): String? {
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
    @JvmStatic
    fun sexToString(context: Context, value: Sex?): String? = when (value) {
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

    /**
     * Required for converting the drug and medical histories, since they store them as a List
     * of Strings.
     */
    @InverseMethod("stringToSingleElementList")
    @JvmStatic
    fun singleElementListToString(list: List<String>?): String =
        if (list?.isNotEmpty() == true) list[0] else ""

    @JvmStatic fun stringToSingleElementList(string: String): List<String>? = listOf(string)

    @JvmStatic fun launchReasonToSaveButtonString(
        context: Context,
        launchReason: ReadingActivity.LaunchReason
    ) = context.run {
        when (launchReason) {
            ReadingActivity.LaunchReason.LAUNCH_REASON_NEW ->
                getString(R.string.fragment_advice_save_new_patient_and_reading_button)
            ReadingActivity.LaunchReason.LAUNCH_REASON_EDIT_READING ->
                getString(R.string.fragment_advice_save_edits_button)
            else ->
                getString(R.string.fragment_advice_save_reading_button)
        }
    }
}
