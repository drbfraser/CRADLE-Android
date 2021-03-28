package com.cradleVSA.neptune.utilitiles

import java.math.BigInteger

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7

private const val DAYS_PER_MONTH = 30 // on average

private const val SECONDS_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
private const val SECONDS_PER_WEEK =
    DAYS_PER_WEEK * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
private const val SECONDS_PER_MONTH =
    DAYS_PER_MONTH * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE

/**
 * A duration of time in a specific unit.
 */
sealed class Duration {
    abstract val seconds: Seconds
}

data class Seconds(val value: Long) : Duration() {
    override val seconds = this

    // Flooring is acceptable for seconds.
    constructor(double: Double) : this(double.toLong())

    // BigInteger to Long conversion should be acceptable as a Duration out of
    // Long range is unrealistic.
    constructor(bigInt: BigInteger) : this(bigInt.toLong())
}

data class Days(val value: Long) : Duration() {
    override val seconds = Seconds(value * SECONDS_PER_DAY)

    constructor(seconds: Seconds) : this(seconds.value / SECONDS_PER_DAY)
}

data class Weeks(val value: Long) : Duration() {
    override val seconds = Seconds(value * SECONDS_PER_WEEK)

    constructor(seconds: Seconds) : this(seconds.value / SECONDS_PER_WEEK)
}

data class Months(val value: Double) : Duration() {
    override val seconds = Seconds(value * SECONDS_PER_MONTH)

    constructor(value: Long) : this(value.toDouble())

    constructor(seconds: Seconds) : this(seconds.value / SECONDS_PER_MONTH.toDouble())
}
