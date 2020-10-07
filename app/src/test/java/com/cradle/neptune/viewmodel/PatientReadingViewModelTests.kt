package com.cradle.neptune.viewmodel

import com.cradle.neptune.model.BloodPressure
import com.cradle.neptune.model.GestationalAgeWeeks
import com.cradle.neptune.model.Sex
import com.cradle.neptune.model.UrineTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.threeten.bp.ZonedDateTime

class PatientReadingViewModelTests {

    /**
     * This test runs through the process of constructing the patient and
     * reading models when the user has supplied input for each field in
     * the view model when constructing a new reading with no existing
     * patient/reading.
     */
    @Test
    fun modelConstruction_emulateUserInput_allFields() {
        // TODO: Redo this test using the new LiveData setup.
    }
}