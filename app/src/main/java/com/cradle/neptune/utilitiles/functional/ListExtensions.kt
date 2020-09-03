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
        throw IllegalArgumentException("empty list")
    }

    val first = first()
    val rest = drop(1)
    return Pair(first, rest)
}

/**
 * Accumulates values in a list starting from left and moving right with the
 * first element of the list being the initial value.
 *
 * @param operation the operation used to accumulate values
 * @throws IllegalArgumentException if the list is empty
 * @return the accumulated result
 */
fun <T> List<T>.fold1(operation: (T, T) -> T): T {
    val (first, rest) = uncons()
    return rest.fold(first, operation)
}
