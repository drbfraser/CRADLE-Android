package com.cradle.neptune.ext

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

fun Context.getEnglishResources(): Resources =
    Configuration(this.resources.configuration)
        .apply { setLocale(Locale.ENGLISH) }
        .let { configuration ->
            this.createConfigurationContext(configuration).resources
        }

@Deprecated(
    "delete this when PatientProfileActivity is converted to Kotlin, as it" +
        "can't use extension functions"
)
class ContextUtil {
    companion object {
        @Deprecated(
            "delete this when PatientProfileActivity is converted to Kotlin," +
                "as Java can't use extension functions",
            ReplaceWith("getEnglishResources()")
        )
        @JvmStatic
        fun getEnglishResources(context: Context) = context.getEnglishResources()
    }
}
