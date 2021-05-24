package com.cradleplatform.neptune.ext

import android.content.SharedPreferences

/**
 * Returns the preference value if it exists, or null. Throws ClassCastException
 * @throws ClassCastException If there is a preference with this name that is not an int.
 */
fun SharedPreferences.getIntOrNull(key: String): Int? {
    val intValue = getInt(key, Int.MIN_VALUE)
    return if (intValue == Int.MIN_VALUE && !contains(key)) {
        null
    } else {
        intValue
    }
}
