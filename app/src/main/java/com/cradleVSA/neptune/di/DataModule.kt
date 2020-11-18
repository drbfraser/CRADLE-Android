package com.cradleVSA.neptune.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.HealthFacilityDao
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.net.Http
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

/**
 * Provide the singleton objects for data access
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@InstallIn(ApplicationComponent::class)
@Module
class DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context) = CradleDatabase.getInstance(context)

    @Provides
    fun providePatientDao(database: CradleDatabase): PatientDao =
        database.patientDao()

    @Provides
    fun provideReadingDao(database: CradleDatabase): ReadingDao =
        database.readingDao()

    @Provides
    fun provideHealthFacilityDao(database: CradleDatabase): HealthFacilityDao =
        database.healthFacility()

    @Provides
    @Singleton
    fun provideHealthCentreService(database: CradleDatabase): HealthFacilityManager {
        return HealthFacilityManager(database)
    }

    @Provides
    @Singleton
    fun providesSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun providesHttp(): Http = Http()
}
