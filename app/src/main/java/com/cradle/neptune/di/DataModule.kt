package com.cradle.neptune.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityDaoAccess
import com.cradle.neptune.database.PatientDaoAccess
import com.cradle.neptune.database.ReadingDaoAccess
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.net.Http
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
    fun provideDatabase(@ApplicationContext context: Context): CradleDatabase {
        return Room.databaseBuilder(
            context,
            CradleDatabase::class.java,
            "room-readingDB"
        ).build()
    }

    @Provides
    fun providePatientDao(database: CradleDatabase): PatientDaoAccess =
        database.patientDaoAccess()

    @Provides
    fun provideReadingDao(database: CradleDatabase): ReadingDaoAccess =
        database.readingDaoAccess()

    @Provides
    fun provideHealthFacilityDao(database: CradleDatabase): HealthFacilityDaoAccess =
        database.healthFacilityDaoAccess()

    @Provides
    @Singleton
    fun provideHealthCentreService(database: CradleDatabase): HealthCentreManager {
        return HealthCentreManager(database)
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
