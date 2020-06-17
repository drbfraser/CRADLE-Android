package com.cradle.neptune.utilitiles

/**
 * Returns `null` if this string is empty, otherwise returns the string itself.
 */
fun String.nullIfEmpty() = if (isEmpty()) null else this
