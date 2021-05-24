package com.cradleplatform.neptune.ext

/**
 * Like [find], but it returns the index if found or null if not found.
 */
inline fun <T> Array<out T>.findIndex(predicate: (T) -> Boolean): Int? {
    for (i in 0 until size) if (predicate(get(i))) return i
    return null
}
