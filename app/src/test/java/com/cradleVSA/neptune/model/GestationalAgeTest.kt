package com.cradleVSA.neptune.model

import com.cradleVSA.neptune.utilitiles.Days
import com.cradleVSA.neptune.utilitiles.Months
import com.cradleVSA.neptune.utilitiles.Weeks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.floor

internal class GestationalAgeTest {

    /**
     * Simulates input in the patient info fragment, inputting months for the gestational age and
     * expecting to get the same month back.
     */
    @Test
    fun simulateInputInPatientInfoFragment_months() {
        for (_months in 0..100) {
            val months: Long = _months.toLong()
            val gestationalAgeMonths = GestationalAgeMonths(Months(months))

            assertEquals(months.toDouble(), gestationalAgeMonths.age.asMonths(), 0.0000000001)

            val weeks = gestationalAgeMonths.age.asWeeks()
            // We use 30 days per month,
            val expectedWeeks: Double = (30 * months) / 7.0
            assertEquals(expectedWeeks, weeks, 0.0000000001)

            val weeksWholeNumber = gestationalAgeMonths.age.weeks.toDouble()
            assertEquals(floor(expectedWeeks), weeksWholeNumber, 0.0000000001)
        }
    }

    /**
     * Simulates input in the patient info fragment, inputting weeks for the gestational age and
     * expecting to get the same weeks back.
     */
    @Test
    fun simulateInputInPatientInfoFragment_weeks() {
        for (_weeks in 0..500) {
            val weeks: Long = _weeks.toLong()
            val gestationalAgeWeeks = GestationalAgeWeeks(Weeks(weeks))

            assertEquals(weeks.toDouble(), gestationalAgeWeeks.age.asWeeks(), 0.0000000001)

            val months = gestationalAgeWeeks.age.asMonths()
            // (expectedWeeks * 7) days, and there are 30 days per month,
            val expectedMonths: Double = (weeks * 7) / 30.0
            assertEquals(expectedMonths, months, 0.0000000001)
        }
    }

    /**
     * Simulates input in the patient info fragment, inputting days for the gestational age and
     * expecting to get the same days back.
     */
    @Test
    fun simulateInputInPatientInfoFragment_days() {
        for (_days in 0..500) {
            val days: Long = _days.toLong()
            val gestationalAgeDays = GestationalAgeDays(Days(days))

            assertEquals(days % 7, gestationalAgeDays.age.days)
            assertEquals(days / 7, gestationalAgeDays.age.weeks)
        }
    }

    /**
     * Simulates input in the patient info fragment, inputting weeks for the gestational age and
     * expecting to get the same weeks back.
     */
    @Test
    fun inputFractionalMonths_getCorrectResult() {
        val twoWeeksAndOneDayAsMonth = Months(0.5)
        val gestationalAgeMonths = GestationalAgeMonths(twoWeeksAndOneDayAsMonth)
        // error("${System.currentTimeMillis() / 1000L - twoWeeksAndOneDayAsMonth.seconds.value} vs ${gestationalAgeMonths.timestamp}")
        assertEquals(2, gestationalAgeMonths.age.weeks)
        assertEquals(1, gestationalAgeMonths.age.days)
    }
}