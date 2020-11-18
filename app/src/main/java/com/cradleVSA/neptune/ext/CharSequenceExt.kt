package com.cradleVSA.neptune.ext

/**
 * Splits this char sequence to a list of strings around occurrences of the specified
 * [delimiter].
 *
 * This is specialized version of [split] which applies the [action] to each string that gets split.
 * Useful when trying to split a string and adding trimmed versions to a list.
 *
 * Original copyright notice:
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license
 *
 * @param delimiter String used as delimiter.
 * @param ignoreCase `true` to ignore character case when matching a delimiter. By default `false`.
 * @param limit The maximum number of substrings to act on. By default 0.
 * @param action The action to perform on each substring.
 */
inline fun CharSequence.splitAndForEach(
    delimiter: String,
    ignoreCase: Boolean = false,
    limit: Int = 0,
    action: (String) -> Unit
) {
    require(limit >= 0) { "Limit must be non-negative, but was $limit." }
    var currentOffset = 0
    var nextIndex = indexOf(delimiter, currentOffset, ignoreCase)
    if (nextIndex == -1 || limit == 1) {
        action(this.toString())
        return
    }

    val isLimited = limit > 0
    var numActedOn = 0
    do {
        action(substring(currentOffset, nextIndex))
        numActedOn++

        currentOffset = nextIndex + delimiter.length
        // Do not search for next occurrence if we're reaching limit
        if (isLimited && numActedOn == limit - 1) break
        nextIndex = indexOf(delimiter, currentOffset, ignoreCase)
    } while (nextIndex != -1)

    action(substring(currentOffset, length))
}
