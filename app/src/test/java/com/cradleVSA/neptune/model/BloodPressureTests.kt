package com.cradleVSA.neptune.model

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BloodPressureTests {

    private val mockContext: Context = mockk()

    @BeforeEach
    fun beforeEach() {
        every { mockContext.getString(any(), *anyVararg()) } answers { getMockStringFromResId() }
        every { mockContext.getString(any()) } answers { getMockStringFromResId() }
    }

    private fun getMockStringFromResId(): String {
        return "mocked error message"
    }

    @Test
    fun bloodPressure_ifInSevereShock_thenRedDown() {
        val bp = BloodPressure(80, 60, 160)
        // i.e, shock index = 2.0
        assertEquals(ReadingAnalysis.RED_DOWN, bp.analysis)
    }

    @Test
    fun bloodPressure_ifVeryHighSystolic_thenRedUp() {
        val bp = BloodPressure(RED_SYSTOLIC, 60, 60)
        assertEquals(ReadingAnalysis.RED_UP, bp.analysis)
    }

    @Test
    fun bloodPressure_ifVeryHighDiastolic_thenRedUp() {
        val bp = BloodPressure(100, RED_DIASTOLIC, 60)
        assertEquals(ReadingAnalysis.RED_UP, bp.analysis)
    }

    @Test
    fun bloodPressure_ifInShock_thenYellowDown() {
        val bp = BloodPressure(80, 60, 80)
        // i.e., shock index = 1.0
        assertEquals(ReadingAnalysis.YELLOW_DOWN, bp.analysis)
    }

    @Test
    fun bloodPressure_ifHighSystolic_thenYellowUp() {
        val bp = BloodPressure(YELLOW_SYSTOLIC, 60, 60)
        assertEquals(ReadingAnalysis.YELLOW_UP, bp.analysis)
    }

    @Test
    fun bloodPressure_ifHighDiastolic_thenYellowUp() {
        val bp = BloodPressure(100, YELLOW_DIASTOLIC, 60)
        assertEquals(ReadingAnalysis.YELLOW_UP, bp.analysis)
    }

    @Test
    fun bloodPressure_ifNormal_thenGreen() {
        val bp = BloodPressure(100, 60, 60)
        assertEquals(ReadingAnalysis.GREEN, bp.analysis)
    }

    @Test
    fun bloodPressure_verify() {
        val (badSystolic, goodSystolic) = createBadAndGoodValues(MIN_SYSTOLIC, MAX_SYSTOLIC)
        val (badDiastolic, goodDiastolic) = createBadAndGoodValues(MIN_DIASTOLIC, MAX_DIASTOLIC)
        val (badHeartrate, goodHeartrate) = createBadAndGoodValues(MIN_HEART_RATE, MAX_HEART_RATE)


        for (systolic in (badSystolic union goodSystolic)) {
            for (diastolic in (badDiastolic union goodDiastolic)) {
                for (heartrate in (badHeartrate union goodHeartrate)) {
                    val expectedValid = (systolic in goodSystolic) &&
                        (diastolic in goodDiastolic) &&
                        (heartrate in goodHeartrate)
                    BloodPressure(systolic, diastolic, heartrate).run {
                        if (expectedValid) {
                            with (this.getAllMembersWithInvalidValues(mockContext)) {
                                assertEquals(0, size) {
                                "expected all to be valid for ${this}, " +
                                    "passed in $systolic, $diastolic, $heartrate; " +
                                    "error messages $this"
                                }
                            }
                        } else {
                            val (actualSystolicValid, sysErrorMsg) = this.isPropertyValid(
                                BloodPressure::systolic, mockContext
                            ).let {
                                (it is Verifiable.Valid) to (it as? Verifiable.Invalid)?.errorMessage
                            }
                            assertEquals((systolic in goodSystolic), actualSystolicValid) {
                                generateAssertErrorMsg(
                                    name = BloodPressure::systolic.name, value = systolic,
                                    lowerBound = MIN_SYSTOLIC, upperBound = MAX_SYSTOLIC,
                                    isValidExpected = (systolic in goodSystolic),
                                    actualValid = actualSystolicValid,
                                    errorMessage = sysErrorMsg,
                                    instance = this,
                                    goodValuesTested = goodSystolic
                                )
                            }

                            val (actualDiastolicValid, diasErrorMsg) = this.isPropertyValid(
                                BloodPressure::diastolic, mockContext
                            ).let {
                                (it is Verifiable.Valid) to (it as? Verifiable.Invalid)?.errorMessage
                            }
                            assertEquals((diastolic in goodDiastolic), actualDiastolicValid) {
                                generateAssertErrorMsg(
                                    name = BloodPressure::diastolic.name, value = diastolic,
                                    lowerBound = MIN_DIASTOLIC, upperBound = MAX_DIASTOLIC,
                                    isValidExpected = (diastolic in goodDiastolic),
                                    actualValid = actualDiastolicValid,
                                    errorMessage = diasErrorMsg,
                                    instance = this,
                                    goodValuesTested = goodDiastolic
                                )
                            }

                            val (actualHeartrateValid, heartErrorMsg) = this.isPropertyValid(
                                BloodPressure::heartRate, mockContext
                            ).let {
                                (it is Verifiable.Valid) to (it as? Verifiable.Invalid)?.errorMessage
                            }
                            assertEquals((heartrate in goodHeartrate), actualHeartrateValid) {
                                generateAssertErrorMsg(
                                    name = BloodPressure::heartRate.name, value = heartrate,
                                    lowerBound = MIN_HEART_RATE, upperBound = MAX_HEART_RATE,
                                    isValidExpected = (heartrate in goodHeartrate),
                                    actualValid = actualHeartrateValid,
                                    errorMessage = heartErrorMsg,
                                    instance = this,
                                    goodValuesTested = goodHeartrate
                                )
                            }
                        }
                    }
                }
            }
        }
        BloodPressure(MIN_SYSTOLIC - 1, MIN_DIASTOLIC - 1, MIN_HEART_RATE - 1).run {
            assertEquals(3, this.getAllMembersWithInvalidValues(mockContext).size)
        }

        BloodPressure(MAX_SYSTOLIC + 1, MAX_DIASTOLIC + 1, MAX_HEART_RATE + 1).run {
            assertEquals(3, this.getAllMembersWithInvalidValues(mockContext).size)
        }
    }

    /**
     * @return Pair(bad, good)
     */
    private fun createBadAndGoodValues(lowerBound: Int, upperBound: Int) =
        Pair(
            setOf(
                0,
                lowerBound - 500,
                lowerBound - 50,
                lowerBound - 1,
                upperBound + 1,
                upperBound + 50,
                upperBound + 500
            ),
            setOf(
                lowerBound,
                lowerBound + (upperBound - lowerBound) / 4,
                lowerBound + (upperBound - lowerBound) / 2,
                lowerBound + 3 * (upperBound - lowerBound) / 4,
                upperBound
            )
        )

    private fun generateAssertErrorMsg(
        name: String,
        value: Int,
        lowerBound: Int,
        upperBound: Int,
        isValidExpected: Boolean,
        actualValid: Boolean,
        errorMessage: String?,
        instance: BloodPressure,
        goodValuesTested: Set<Int>
    ) = "tested $name value is: $value\n" +
        "$name bounds: $lowerBound - $upperBound\n" +
        "validity expected: $isValidExpected\n" +
        "but got validity result: $actualValid\n" +
        (if (!actualValid) "with error message: $errorMessage\n" else "") +
        "on instance: $instance\n" +
        "and expected good values are: $goodValuesTested"
}
