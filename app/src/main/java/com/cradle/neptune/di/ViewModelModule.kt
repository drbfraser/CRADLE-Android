package com.cradle.neptune.di

import android.content.Context
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.viewmodel.PatientReadingViewModelFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@InstallIn(ApplicationComponent::class)
@Module
class ViewModelModule {
    @Provides
    @Singleton
    fun providesPatientReadingViewModelFactory(
        readingManager: ReadingManager,
        patientManager: PatientManager,
        @ApplicationContext context: Context
    ) = PatientReadingViewModelFactory(readingManager, patientManager, context)
}
