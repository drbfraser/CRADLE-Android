package com.cradle.neptune.utilitiles.functional

/**
 * Splits this list after the first element and returns both the first element
 * and the rest of the list as a pair.
 *
 * @throws IllegalArgumentException if the list is empty
 * @return the first and rest of the list
 */
fun <T> List<T>.uncons(): Pair<T, List<T>> {
    if (isEmpty()) {
        throw IllegalArgumentException("uncons called on empty list")
    }

    val first = first()
    val rest = drop(1)
    return Pair(first, rest)
}
