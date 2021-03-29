package com.cradleVSA.neptune.utilitiles

import java.io.Serializable
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
interface Duration {
    val seconds: Seconds

    /**
     * Returns -1 if this < [other], 0 if this == [other] and if this > [other].
     */
    operator fun compareTo(other: Duration): Int = when {
        seconds.value < other.seconds.value -> -1
        seconds.value == other.seconds.value -> 0
        else -> 1
    }
}

inline class Seconds(val value: Long) : Duration {
    override val seconds
        get() = this

    // Flooring is acceptable for seconds.
    constructor(double: Double) : this(double.toLong())

    // BigInteger to Long conversion should be acceptable as a Duration out of
    // Long range is unrealistic.
    constructor(bigInt: BigInteger) : this(bigInt.toLong())
}

inline class Weeks(val value: Long) : Duration {
    override val seconds
        get() = Seconds(value * SECONDS_PER_WEEK)

    companion object {
        fun fromSeconds(seconds: Seconds) = Weeks(seconds.value / SECONDS_PER_WEEK)
    }
}

inline class Months(val value: Double) : Duration {
    override val seconds
        get() = Seconds(value * SECONDS_PER_MONTH)

    constructor(value: Long) : this(value.toDouble())

    companion object {
        fun fromSeconds(seconds: Seconds) = Months(
            seconds.value / SECONDS_PER_MONTH.toDouble()
        )
    }
}

/**
 * A temporal duration expressed in weeks and days.
 */
inline class WeeksAndDays(val totalDays: Long) : Duration, Serializable {
    constructor(weeks: Long, days: Long) : this(weeks * DAYS_PER_WEEK + days)

    val days: Long get() = totalDays % DAYS_PER_WEEK
    val weeks: Long get() = totalDays / DAYS_PER_WEEK

    override val seconds: Seconds get() = Seconds(totalDays * SECONDS_PER_DAY)

    /**
     * This value in number of weeks.
     */
    fun asWeeks(): Double = weeks.toDouble() + (days.toDouble() / DAYS_PER_WEEK)

    /**
     * This value in number of months.
     */
    fun asMonths(): Double = totalDays / DAYS_PER_MONTH.toDouble()

    companion object {
        const val DAYS_PER_WEEK = 7
        const val DAYS_PER_MONTH = 30 // on average
        // Get as close as possible to the result of 30/7, a repeating decimal. However, due to the
        // finiteness of Doubles, anything past 4.2857142857142857 is ignored.
        private const val WEEKS_PER_MONTH = 4.2857142857142857

        fun fromSeconds(seconds: Seconds): WeeksAndDays {
            return WeeksAndDays(seconds.value / SECONDS_PER_DAY)
        }
    }

    override fun toString(): String = "WeeksAndDays(weeks=$weeks, days=$days, " +
        "asWeeks()=${asWeeks()}, asMonths()=${asMonths()})"
}
