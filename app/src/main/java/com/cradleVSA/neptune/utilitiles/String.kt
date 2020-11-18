package com.cradleVSA.neptune.utilitiles

/**
 * Returns `null` if this string is empty, otherwise returns the string itself.
 */
fun String.nullIfEmpty() = if (isEmpty()) null else this
