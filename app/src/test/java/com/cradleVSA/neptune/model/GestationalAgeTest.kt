package com.cradleVSA.neptune.model

import com.cradleVSA.neptune.utilitiles.Months
import com.cradleVSA.neptune.utilitiles.Weeks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.math.floor

internal class GestationalAgeTest {
    @Test
    fun `test equality`() {
        val timestampA = 50.toBigInteger()
        val timestampB = 51.toBigInteger()
        assertEquals(GestationalAgeWeeks(timestampA), GestationalAgeWeeks(timestampA))
        assertEquals(GestationalAgeMonths(timestampA), GestationalAgeMonths(timestampA))
        assertNotEquals(GestationalAgeWeeks(timestampA), GestationalAgeMonths(timestampA))
        assertNotEquals(GestationalAgeMonths(timestampA), GestationalAgeMonths(timestampB))
        assertNotEquals(GestationalAgeWeeks(timestampA), GestationalAgeWeeks(timestampB))
        assertNotEquals(GestationalAgeWeeks(timestampA), GestationalAgeMonths(timestampB))
    }

    /**
     * Simulates input in the patient info fragment, inputting months for the gestational age and
     * expecting to get the same month back.
     */
    @Test
    fun simulateInputInPatientInfoFragment_months() {
        for (_months in 0..100) {
            val months: Long = _months.toLong()
            val gestationalAgeMonths = GestationalAgeMonths(Months(months))

            assertEquals(months.toDouble(), gestationalAgeMonths.ageFromNow.asMonths(), 0.0000000001)

            val weeks = gestationalAgeMonths.ageFromNow.asWeeks()
            // We use 30 days per month,
            val expectedWeeks: Double = (30 * months) / 7.0
            assertEquals(expectedWeeks, weeks, 0.0000000001)

            val weeksWholeNumber = gestationalAgeMonths.ageFromNow.weeks.toDouble()
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

            assertEquals(weeks.toDouble(), gestationalAgeWeeks.ageFromNow.asWeeks(), 0.0000000001)

            val months = gestationalAgeWeeks.ageFromNow.asMonths()
            // (expectedWeeks * 7) days, and there are 30 days per month,
            val expectedMonths: Double = (weeks * 7) / 30.0
            assertEquals(expectedMonths, months, 0.0000000001)
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
        assertEquals(2, gestationalAgeMonths.ageFromNow.weeks)
        assertEquals(1, gestationalAgeMonths.ageFromNow.days)
    }
}