package com.cradle.neptune.dagger

import android.app.Application
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ViewModelModule {
    @Provides
    @Singleton
    fun providesPatientReadingViewModelFactory(
        readingManager: ReadingManager,
        patientManager: PatientManager,
        application: Application
    ) = PatientReadingViewModelFactory(readingManager, patientManager, application)
}
