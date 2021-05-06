package com.cradleVSA.neptune.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30 // on average

private const val SECONDS_PER_WEEK =
    DAYS_PER_WEEK * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE
private const val SECONDS_PER_MONTH =
    DAYS_PER_MONTH * HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE

class DurationTest {

    @Test
    fun months_doubleAsWholeNumber() {
        val secondsUsingDouble = Months(5.0).seconds.value
        val expectedSeconds = Months(5).seconds.value
        assertEquals(expectedSeconds, secondsUsingDouble)
    }

    @Test
    fun months_doubleAsFractional() {
        val halfAMonth = Months(0.5)
        assertEquals(
            0.5 * SECONDS_PER_MONTH,
            halfAMonth.seconds.value.toDouble(),
            0.0000000000001
        )

        arrayOf(
            12.0001, 12.01, 12.1, 12.2, 12.3, 12.4, 12.5, 12.5555, 12.6, 12.7, 12.8, 12.9, 12.99,
            12.999
        ).forEach {
            val secondsUsingDouble = Months(it)
            assert(Months(12) <= secondsUsingDouble)
            assert(secondsUsingDouble <= Months(13))

            val expectedSeconds = (it * SECONDS_PER_MONTH).toLong()
            assertEquals(expectedSeconds, secondsUsingDouble.seconds.value)
        }
    }

    @Test
    fun months_longInput() {
        arrayOf<Long>(0, 3, 6, 9, 12, 50, 100)
            .forEach {
                val month = Months(it)
                // We're not going to use more than 100 months, so we don't really
                // see the risk of overflow in regular use.
                assert(it.toDouble() == month.value)

                val secondsFromTimeUnit = TimeUnit.DAYS.toSeconds(it * DAYS_PER_MONTH)
                assertEquals(secondsFromTimeUnit, month.seconds.value)
            }
    }
}