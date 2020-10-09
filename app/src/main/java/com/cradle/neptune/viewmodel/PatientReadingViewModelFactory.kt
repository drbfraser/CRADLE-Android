package com.cradle.neptune.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import javax.inject.Inject

/**
 * Example adapted from
 * https://github.com/android/sunflower/blob/69472521b0e0dea9ffb41db223d6ee1cb27bd557/app/src/main
 *       /java/com/google/samples/apps/sunflower/viewmodels/GardenPlantingListViewModelFactory.kt
 */
class PatientReadingViewModelFactory @Inject constructor(
    private val readingManager: ReadingManager,
    private val patientManager: PatientManager,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return PatientReadingViewModel(readingManager, patientManager, application) as T
    }
}
