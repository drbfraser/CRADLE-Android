package com.cradleplatform.neptune.model

import android.content.Context
import android.text.TextUtils
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.utilities.Months
import com.cradleplatform.neptune.utilities.Weeks
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.reflect.KProperty

@ExtendWith(MockKExtension::class)
class PatientTests {
    private val NON_ALPHANUMERIC_STRINGS = setOf(
        "this is my email__", "hi@gmail.com", "thisIS@#*(U", "0x10*!@#$%^&*", "-/*-/*+",
        "0*(1+0)*"
    )
    private val BLANK_AND_NULL_STRINGS = setOf("", " ", "   ", null)

    private val mockContext: Context = mockk()


    @BeforeEach
    fun beforeEach() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isDigitsOnly(any()) } answers {
            // Search for any non-digit strings. Null is returned if no non-digit strings are found
            (arg<CharSequence>(0)).find { !it.isDigit() } == null
        }

        clearAndApplyMocksToMockContext()
    }

    /**
     * Cleans out and reapply the mocks to the mocked Context. This is helpful when looping through
     * test values to verify error messages.
     */
    private fun clearAndApplyMocksToMockContext() {
        clearMocks(mockContext)
        every { mockContext.getString(any(), *anyVararg()) } answers { getMockStringFromResId(arg(0)) }
        every { mockContext.getString(any()) } answers { getMockStringFromResId(arg(0)) }
    }

    @Test
    fun patient_jackson_deserializeAndSerialize() {
        val possiblePairsToTest = arrayOf(
            CommonPatientReadingJsons.patientNoGestAgeJsonAndExpected,
            CommonPatientReadingJsons.patientWithGestAgeJsonAndExpected,
            CommonPatientReadingJsons.patientWithReferralAndFollowup
        ).map { it.first to it.second.patient }

        for ((jsonStringOfPatientAndReadings, expectedPatient) in possiblePairsToTest) {
            val reader = JacksonMapper.readerForPatient
            val parsedPatient = reader.readValue<Patient>(jsonStringOfPatientAndReadings)
            assertEquals(expectedPatient, parsedPatient)

            val writer = JacksonMapper.writerForPatient
            val serializePatientAndReadings = writer.writeValueAsString(parsedPatient)

            assertPatientJsonEqual(
                jsonStringForExpected = jsonStringOfPatientAndReadings,
                jsonStringForActual = serializePatientAndReadings
            )
        }
    }

    @Test
    fun deserialize_isTheInverseOf_serialize() {
        val patient = Patient(
            id = "5414842504",
            name = "AB",
            dateOfBirth = "1989-10-24",
            isExactDateOfBirth = true,
            gestationalAge = GestationalAgeWeeks(Weeks(28)),
            sex = Sex.FEMALE,
            isPregnant = true,
            zone = "9945",
            villageNumber = "2342",
            householdNumber = "345345",
            drugHistory = "aab",
            medicalHistory = ""
        )

        val writer = JacksonMapper.createWriter<Patient>()
        val json = writer.writeValueAsString(patient)

        val reader = JacksonMapper.createReader<Patient>()
        val actual = reader.readValue<Patient>(json)
        assertEquals(patient, actual)
    }

    @Test
    fun marshal_patientWithReadings_alsoMarshalsReadings() {
        val patientId = "2345452362"
        val makeReading = { id: String ->
            Reading(
                id = id,
                patientId = patientId,
                dateTaken = 123,
                lastEdited = 123,
                bloodPressure = BloodPressure(110, 80, 70),
                symptoms = emptyList(),
                referral = null,
                followUp = null,
                urineTest = null,
                dateRetestNeeded = null,
                isFlaggedForFollowUp = false,
                isUploadedToServer = false,
                userId = 1
            )
        }

        val patient = Patient(
            id = patientId,
            name = "AB",
            dateOfBirth = "1989-11-11",
            isExactDateOfBirth = false,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        val patientAndReadings = PatientAndReadings(patient, listOf("a", "b", "c").map(makeReading))

        val writer = JacksonMapper.createWriter<PatientAndReadings>()
        val json = writer.writeValueAsString(patientAndReadings)

        val reader = JacksonMapper.createReader<PatientAndReadings>()
        val actual = reader.readValue<PatientAndReadings>(json)
        assertEquals(patientAndReadings.patient, actual.patient)
        assertEquals(patientAndReadings.readings, actual.readings)
    }

    @Test
    fun verify_patientId() {
        val wrong =
            setOf("", "  ", "123456789012345", "1234567890123457890123", "abc", "i am not") union
                NON_ALPHANUMERIC_STRINGS
        val good = setOf("0", "23", "53345", "23523", "12345678901234", "345983798")
        assertValidityOverSet(wrong, Patient::id, areAllValuesValid = false)
        assertValidityOverSet(good, Patient::id, areAllValuesValid = true)
    }

    @Test
    fun verify_patientName() {
        val wrong = setOf("", " ", "sad_345", "11", "Johh5", "3453453453543 5 345435 345345",
            "123456789012345", "ABCDFGHJKLQWE5RT")
        val good = setOf("testName", "John Smith", "Someone", "Alice", "Bob", "ABC", "JKL",
            "Zulo", "Alice Bob Eden", "SomeRealLongNameWillHaveFourCharWordAlwa", "Jake O'Henry",
            "Sir Name-ham Namer")
        assertValidityOverSet(wrong, Patient::name, areAllValuesValid = false)
        assertValidityOverSet(good, Patient::name, areAllValuesValid = true)
    }

    @Test
    fun verify_patientDateOfBirth_withPatientInstance() {
        val wrongFormat = setOf(
            "sad_345", "11", "Johh5", "3453453453543 5 345435 345345",
            "123456789012345", "ABCDFGHJKLQWE5RT", "23-2-2004", "1-20-2004", "1-20-2004",
            "1995-99-0", "1995-356-678", "None"
        )
        val badAgeButGoodFormat = setOf("2015-05-22", "2017-04-08", "2020-05-01", "1900-04-01", "1850-03-01")
        val good = setOf("1980-05-22", "1995-04-08", "1995-05-01")

        val patientWithoutAge = Patient(
            id = "3453455",
            name = "AB",
            dateOfBirth = null,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        // We expect null / empty date of birth to be invalid.
        assertValidityOverSet(
            BLANK_AND_NULL_STRINGS, Patient::dateOfBirth,
            areAllValuesValid = false, patientInstance = patientWithoutAge
        )
        verify(exactly = BLANK_AND_NULL_STRINGS.size) {
            mockContext.getString(R.string.patient_error_age_or_dob_missing)
        }

        // Wrong format.
        assertValidityOverSet(
            wrongFormat, Patient::dateOfBirth,
            areAllValuesValid = false, patientInstance = patientWithoutAge
        )
        verify(exactly = wrongFormat.size) {
            mockContext.getString(R.string.patient_error_dob_format, *anyVararg())
        }

        // Age out of bounds.
        assertValidityOverSet(
            badAgeButGoodFormat, Patient::dateOfBirth,
            areAllValuesValid = false, patientInstance = patientWithoutAge
        )
        verify(exactly = badAgeButGoodFormat.size) {
            mockContext.getString(
                R.string.patient_error_age_between_n_and_m,
                Patient.AGE_LOWER_BOUND, Patient.AGE_UPPER_BOUND
            )
        }

        assertValidityOverSet(
            good, Patient::dateOfBirth, areAllValuesValid = true, patientInstance = patientWithoutAge
        )
    }

    @Test
    fun verify_patientDateOfBirth_withoutPatientInstance() {
        val wrong = setOf(
            "sad_345", "11", "Johh5", "3453453453543 5 345435 345345",
            "123456789012345", "ABCDFGHJKLQWE5RT", "23-2-2004", "1-20-2004", "1-20-2004",
            "1995-05-0", "1995-0-99", "1995-99-05", "1995-55-5", "1995-5-35"
        )
        val good = setOf("2004-05-22", "1995-04-08", "1995-05-01")

        assertValidityOverSet(
            good, Patient::dateOfBirth,
            areAllValuesValid = true, patientInstance = null
        )
        verify(exactly = 0) { mockContext.getString(any()) }
        verify(exactly = 0) { mockContext.getString(any(), *anyVararg()) }

        assertValidityOverSet(
            wrong, Patient::dateOfBirth,
            areAllValuesValid = false, patientInstance = null
        )
        verify(exactly = wrong.size) { mockContext.getString(any(), *anyVararg()) }
    }

    @Test
    fun verify_gestationalAgeWeeks_withPatientInstance() {
        val patientFemalePregnant = Patient(
            id = "3453455", name = "TEST", dateOfBirth = "1989-10-10",
            isExactDateOfBirth = true, sex = Sex.FEMALE, isPregnant = true
        )
        val patientOtherPregnant = Patient(
            id = "3453455", name = "TEST", dateOfBirth = "1989-10-10",
            isExactDateOfBirth = true, sex = Sex.OTHER, isPregnant = true
        )
        val patientFemaleNotPregnant = Patient(
            id = "3453455", name = "TEST", dateOfBirth = "1989-10-10",
            isExactDateOfBirth = true, sex = Sex.FEMALE, isPregnant = false
        )
        val patientOtherNotPregnant = Patient(
            id = "3453455", name = "TEST", dateOfBirth = "1989-10-10",
            isExactDateOfBirth = true, sex = Sex.OTHER, isPregnant = false
        )
        val patientMale = Patient(
            id = "3453455", name = "TEST", dateOfBirth = "1989-10-10",
            isExactDateOfBirth = true, sex = Sex.MALE, isPregnant = false
        )

        // Testing missing gestational age and age of 0
        assertValidityOverSet(
            setOf(null, GestationalAgeWeeks(Weeks(0L))), Patient::gestationalAge,
            areAllValuesValid = false, patientInstance = patientFemalePregnant
        )
        // calls to getString happen iff not valid
        verifyOrder {
            mockContext.getString(R.string.patient_error_gestational_age_missing)
            mockContext.getString(R.string.patient_error_gestation_must_be_not_zero)
        }
        verify(exactly = 0) { mockContext.getString(any(), *anyVararg()) }

        /*
         * Testing valid gestational ages
         */
        val validGestationalAges = (1..43).map { GestationalAgeWeeks(Weeks(it.toLong())) }.toSet()

        assertValidityOverSet(
            validGestationalAges, Patient::gestationalAge,
            areAllValuesValid = true, patientInstance = patientFemalePregnant
        )
        // the two errors from the previous run
        verify(exactly = 2) { mockContext.getString(any()) }
        verify(exactly = 0) { mockContext.getString(any(), *anyVararg()) }

        /*
         * Testing invalid gestational ages
         */
        val invalidGestationalAges = (44..120).map {
            GestationalAgeWeeks(Weeks(it.toLong()))
        }.toSet()

        // invalid if gender is female and pregnant and gestational age is out of range
        assertValidityOverSet(
            invalidGestationalAges, Patient::gestationalAge,
            areAllValuesValid = false, patientInstance = patientFemalePregnant
        )
        verify(exactly = 1) { mockContext.getString(R.string.patient_error_gestation_must_be_not_zero) }
        verify(exactly = invalidGestationalAges.size) {
            mockContext.getString(
                R.string.patient_error_gestation_greater_than_n_weeks,
                *varargAny { nArgs == 1 }
            )
        }


        /*
         * Testing gestational ages for other genders
         */
        // invalid if gender is other and pregnant but missing gestational age
        assertValidityOverSet(
            setOf(null, GestationalAgeWeeks(Weeks(0L))), Patient::gestationalAge,
            areAllValuesValid = false, patientInstance = patientOtherPregnant
        )

        // it's fine to be missing gestational age (null) if not pregnant
        assertValidityOverSet(
            setOf(null), Patient::gestationalAge,
            areAllValuesValid = true, patientInstance = patientFemaleNotPregnant
        )

        // it's fine to be missing gestational age (null) if not pregnant
        assertValidityOverSet(
            setOf(null), Patient::gestationalAge,
            areAllValuesValid = true, patientInstance = patientOtherNotPregnant
        )

        // it's fine to be missing gestational age (null) if male
        assertValidityOverSet(
            setOf(null), Patient::gestationalAge,
            areAllValuesValid = true, patientInstance = patientMale
        )
    }

    @Test
    fun verify_gestationalAgeWeeks_withoutPatientInstance() {
        val validGestationalAges = (1..43).map { GestationalAgeWeeks(Weeks(it.toLong())) }.toSet()
        val gestationalAgeNeeded = setOf(
            Pair(Sex.FEMALE, true),
            Pair(Sex.OTHER, true)
        )
        for ((sex, isPregnant) in gestationalAgeNeeded) {
            assertValidityOverSet(
                validGestationalAges, Patient::gestationalAge,
                areAllValuesValid = true, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )

            // invalid if gestational age needed but it's missing
            assertValidityOverSet(
                setOf(null), Patient::gestationalAge,
                areAllValuesValid = false, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )

            // invalid when out of range
            val invalidGestationalAges = (0..0 union 44..120).map {
                GestationalAgeWeeks(Weeks(it.toLong()))
            }.toSet()
            assertValidityOverSet(
                invalidGestationalAges, Patient::gestationalAge,
                areAllValuesValid = false, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
        }

        val gestationalAgeIgnored = setOf(
            Pair(Sex.FEMALE, false),
            Pair(Sex.OTHER, false),
            Pair(Sex.MALE, false),
            Pair(Sex.MALE, true)
        )
        for ((sex, isPregnant) in gestationalAgeIgnored) {
            assertValidityOverSet(
                setOf(null), Patient::gestationalAge,
                areAllValuesValid = true, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
        }
    }

    @Test
    fun verify_gestationalAgeMonths_withoutPatientInstance() {
        val validGestationalAges =
            (1..10).map { GestationalAgeMonths(Months(it.toLong())) }.toSet()

        val validGestationalAgesDouble = mutableSetOf<Double>().apply {
            var doubleValue = 1.0
            while (doubleValue < 9.9) {
                this.add(doubleValue)
                doubleValue += 0.1
            }
        }.map { GestationalAgeMonths(Months(it)) }.toSet()

        val gestationalAgeNeeded = setOf(
            Pair(Sex.FEMALE, true),
            Pair(Sex.OTHER, true)
        )

        for ((sex, isPregnant) in gestationalAgeNeeded) {
            assertValidityOverSet(
                validGestationalAges, Patient::gestationalAge,
                areAllValuesValid = true, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
            assertValidityOverSet(
                validGestationalAgesDouble, Patient::gestationalAge,
                areAllValuesValid = true, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
            verify(exactly = 0) { mockContext.getString(any()) }
            verify(exactly = 0) { mockContext.getString(any(), *varargAny { nArgs == 1 }) }

            // invalid if gestational age needed but it's missing or 0
            assertValidityOverSet(
                setOf(null, GestationalAgeMonths(Months(0L))), Patient::gestationalAge,
                areAllValuesValid = false, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
            verify(exactly = 1) { mockContext.getString(R.string.patient_error_gestational_age_missing) }
            verify(exactly = 1) { mockContext.getString(R.string.patient_error_gestation_must_be_not_zero) }

            // invalid when out of range
            val invalidGestationalAges = (11..50).map {
                GestationalAgeMonths(Months(it.toLong()))
            }.toSet()
            assertValidityOverSet(
                invalidGestationalAges, Patient::gestationalAge,
                areAllValuesValid = false, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
            verify(exactly = invalidGestationalAges.size) {
                mockContext.getString(
                    R.string.patient_error_gestation_greater_than_n_months,
                    *varargAny { nArgs == 1 }
                )
            }

            clearAndApplyMocksToMockContext()
        }

        val gestationalAgeIgnored = setOf(
            Pair(Sex.FEMALE, false),
            Pair(Sex.OTHER, false),
            Pair(Sex.MALE, false),
            Pair(Sex.MALE, true)
        )
        for ((sex, isPregnant) in gestationalAgeIgnored) {
            assertValidityOverSet(
                setOf(null), Patient::gestationalAge,
                areAllValuesValid = true, dependentPropertiesMap = mapOf(
                    Patient::sex to sex, Patient::isPregnant to isPregnant
                )
            )
            verify(exactly = 0) { mockContext.getString(any()) }
            verify(exactly = 0) { mockContext.getString(any(), *varargAny { nArgs == 1 }) }
        }
    }

    /**
     * @param areAllValuesValid If true, all elements in [set] are values that would be invalid for
     * [property]. If false, all elements in [set] are values that would be valid for [property]
     */
    private fun assertValidityOverSet(
        set: Set<*>, property: KProperty<*>,
        areAllValuesValid: Boolean, patientInstance: Patient? = null,
        dependentPropertiesMap: Map<KProperty<*>, Any?>? = null
    ) {
        set.forEach{ setElement ->
            val validityResult = Patient.Companion.isValueValid(
                property, setElement, mockContext, patientInstance,
                dependentPropertiesMap?.mapKeys { it.key.name }?.toMap()
            )
            if (areAllValuesValid) {
                assert(validityResult is Verifiable.Valid) {
                    "expected $setElement to be an valid ${property.name};" +
                        " but got mocked error message \"${(validityResult as Verifiable.Invalid).errorMessage}\""
                }
            } else {
                assert(validityResult is Verifiable.Invalid) {
                    "expected $setElement to be an invalid ${property.name};" +
                        " but it was accepted as valid"
                }
            }
        }
    }

    private fun getMockStringFromResId(resId: Int): String = when (resId) {
        R.string.patient_error_id_missing -> "patient error missing"
        R.string.patient_error_id_too_long_max_n_digits -> "id too long"
        R.string.patient_error_age_or_dob_missing -> "patient age or dob missing"
        R.string.patient_error_dob_format -> "dob format"
        R.string.patient_error_age_between_n_and_m -> "patient age is out of range"
        R.string.patient_error_gestation_greater_than_n_months,
        R.string.patient_error_gestation_greater_than_n_weeks -> "gestation greater than bound"
        else -> "Unmocked string for resource id $resId"
    }
}
