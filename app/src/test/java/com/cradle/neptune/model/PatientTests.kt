package com.cradle.neptune.model

import android.content.Context
import android.text.TextUtils
import com.cradle.neptune.R
import com.cradle.neptune.utilitiles.Weeks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.lang.IllegalArgumentException
import java.util.Calendar
import java.util.TimeZone
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

        every { mockContext.getString(any(), *anyVararg()) } answers { getMockStringFromResId(arg(0)) }
        every { mockContext.getString(any()) } answers { getMockStringFromResId(arg(0)) }
    }

    @Test
    fun unmarshal_isTheInverseOf_marshal() {
        val patient = Patient(
            "5414842504",
            "AB",
            null,
            32,
            GestationalAgeWeeks(28),
            Sex.FEMALE,
            true,
            null,
            null,
            emptyList(),
            emptyList()
        )

        val json = patient.marshal()
        val actual = unmarshal(Patient, json)
        assertEquals(patient, actual)
    }

    @Test
    fun marshal_patientWithReadings_alsoMarshalsReadings() {
        val patientId = "2345452362"
        val makeReading = { id: String ->
            Reading(
                id = id,
                patientId = patientId,
                dateTimeTaken = 123,
                bloodPressure = BloodPressure(110, 80, 70),
                symptoms = emptyList(),
                referral = null,
                followUp = null,
                urineTest = null,
                dateRecheckVitalsNeeded = null,
                isFlaggedForFollowUp = false
            )
        }

        val patient = Patient(
            id = patientId,
            name = "AB",
            dob = null,
            age = 32,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        val patientAndReadings = PatientAndReadings(patient, listOf("a", "b", "c").map(makeReading))

        val json = patientAndReadings.marshal()
        assertTrue(json.has("readings"))
        assertEquals(3, json.getJSONArray("readings").length())
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
            "Zulo", "***ThisIsValid...", "\"SomeRealLongNameWillHaveFourCharWordAlwa\"")
        assertValidityOverSet(wrong, Patient::name, areAllValuesValid = false)
        assertValidityOverSet(good, Patient::name, areAllValuesValid = true)
    }

    @Test
    fun verify_patientDateOfBirth_withPatientInstance() {
        val wrong = setOf("sad_345", "11", "Johh5", "3453453453543 5 345435 345345",
            "123456789012345", "ABCDFGHJKLQWE5RT", "23-2-2004", "1-20-2004", "1-20-2004",
            "1995-05-0", "1995-0-0", "1995-0-05", "1995-0-5", "1995-5-5")
        val good = setOf("2004-05-22", "1995-04-08", "1995-05-01")

        val patientWithoutAge = Patient(
            id = "3453455",
            name = "AB",
            dob = null,
            age = null,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        // Since this patient doesn't have an age, we expect null / empty to be invalid.
        assertValidityOverSet(
            wrong union BLANK_AND_NULL_STRINGS, Patient::dob,
            areAllValuesValid = false, patientInstance = patientWithoutAge
        )
        assertValidityOverSet(good, Patient::dob, areAllValuesValid = true, patientInstance = patientWithoutAge)

        val patientWithAge = Patient(
            id = "3453455",
            name = "AB",
            dob = null,
            age = 30,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        // Since this patient has an age, we expect null / blank / empty to be valid.
        assertValidityOverSet(
            wrong , Patient::dob,
            areAllValuesValid = false, patientInstance = patientWithAge
        )
        assertValidityOverSet(
            good union BLANK_AND_NULL_STRINGS, Patient::dob,
            areAllValuesValid = true, patientInstance = patientWithAge
        )

        // If we don't have any instance to compare it to, we assume it's bad.
        assertThrows(IllegalArgumentException::class.java) {
            assertValidityOverSet(
                BLANK_AND_NULL_STRINGS, Patient::dob,
                areAllValuesValid = false, patientInstance = null
            )
        }
    }

    @Test
    fun verify_patientDateOfBirth_withoutPatientInstance() {
        val wrong = setOf(
            "sad_345", "11", "Johh5", "3453453453543 5 345435 345345",
            "123456789012345", "ABCDFGHJKLQWE5RT", "23-2-2004", "1-20-2004", "1-20-2004",
            "1995-05-0", "1995-0-0", "1995-0-05", "1995-0-5", "1995-5-5"
        )
        val good = setOf("2004-05-22", "1995-04-08", "1995-05-01")

        // If the patient already has some age, then all blank / null values are valid.
        assertValidityOverSet(
            BLANK_AND_NULL_STRINGS, Patient::dob,
            areAllValuesValid = true, patientInstance = null,
            dependentPropertiesMap = mapOf(Patient::age to 50)
        )
        assertValidityOverSet(
            good, Patient::dob,
            areAllValuesValid = true, patientInstance = null,
            dependentPropertiesMap = mapOf(Patient::age to 50)
        )
        assertValidityOverSet(
            wrong, Patient::dob,
            areAllValuesValid = false, patientInstance = null,
            dependentPropertiesMap = mapOf(Patient::age to 50)
        )
    }

    @Test
    fun verify_patientAge_withPatientInstance() {
        val wrong = -100..14 union 66..100
        val good = (15..65).toSet()

        val patientWithoutDob = Patient(
            id = "3453455",
            name = "AB",
            dob = null,
            age = null,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        // Since this patient doesn't have a dob, we expect null age to be invalid.
        assertValidityOverSet(
            wrong union setOf<Int?>(null), Patient::age,
            areAllValuesValid = false, patientInstance = patientWithoutDob
        )
        assertValidityOverSet(
            good, Patient::age,
            areAllValuesValid = true, patientInstance = patientWithoutDob
        )

        val patientWithDob = Patient(
            id = "3453455",
            name = "AB",
            dob = "1990-05-05",
            age = null,
            sex = Sex.FEMALE,
            isPregnant = false
        )
        assertValidityOverSet(
            wrong, Patient::age,
            areAllValuesValid = false, patientInstance = patientWithDob
        )
        // Since this patient has dob, we expect null age to be valid.
        assertValidityOverSet(
            good union setOf<Int?>(null), Patient::age,
            areAllValuesValid = true, patientInstance = patientWithDob
        )
    }

    @Test
    fun verify_gestationalAgeWeeks_withPatientInstance() {
        val patientFemalePregnant = Patient(
            id = "3453455", name = "TEST", dob = null,
            age = null, sex = Sex.FEMALE, isPregnant = true
        )
        val patientOtherPregnant = Patient(
            id = "3453455", name = "TEST", dob = null,
            age = null, sex = Sex.OTHER, isPregnant = true
        )
        val patientFemaleNotPregnant = Patient(
            id = "3453455", name = "TEST", dob = null,
            age = null, sex = Sex.FEMALE, isPregnant = false
        )
        val patientOtherNotPregnant = Patient(
            id = "3453455", name = "TEST", dob = null,
            age = null, sex = Sex.OTHER, isPregnant = false
        )
        val patientMale = Patient(
            id = "3453455", name = "TEST", dob = null,
            age = null, sex = Sex.MALE, isPregnant = false
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
            Pair(Sex.MALE, false)
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

    /**
     * @param areAllValuesValid If true, all elements in [set] are values that would be invalid for
     * [property]. If false, all elements in [set] are values that would be valid for [property]
     */
    private fun assertValidityOverSet(
        set: Set<*>, property: KProperty<*>,
        areAllValuesValid: Boolean, patientInstance: Patient? = null,
        dependentPropertiesMap: Map<KProperty<*>, Any?>? = null
    ) {
        set.forEach{
            val pair = Patient.Companion.isValueValid(
                property, it, mockContext, patientInstance, dependentPropertiesMap
            )
            if (areAllValuesValid) {
                assert(pair.first) {
                    "expected $it to be an valid ${property.name};" +
                        " but got mocked error message \"${pair.second}\""
                }
            } else {
                assert(!pair.first) {
                    "expected $it to be an invalid ${property.name};" +
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
