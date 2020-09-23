package com.cradle.neptune.model

import com.cradle.neptune.utilitiles.Months
import com.cradle.neptune.utilitiles.Weeks
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

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
            val gestationalAgeMonths = GestationalAgeWeeks(Weeks(weeks))

            assertEquals(weeks.toDouble(), gestationalAgeMonths.age.asWeeks(), 0.0000000001)

            val months = gestationalAgeMonths.age.asMonths()
            // (expectedWeeks * 7) days, and there are 30 days per month,
            val expectedMonths: Double = (weeks * 7) / 30.0
            assertEquals(expectedMonths, months, 0.0000000001)
        }
    }
}