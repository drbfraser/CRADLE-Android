package com.cradleVSA.neptune.di

import android.content.Context
import android.content.SharedPreferences
import com.cradleVSA.neptune.cryptography.EncryptedSharedPreferencesCustom
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.database.daos.HealthFacilityDao
import com.cradleVSA.neptune.database.daos.PatientDao
import com.cradleVSA.neptune.database.daos.ReadingDao
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.manager.PatientManager
import com.cradleVSA.neptune.manager.ReadingManager
import com.cradleVSA.neptune.manager.UrlManager
import com.cradleVSA.neptune.model.Settings
import com.cradleVSA.neptune.net.Http
import com.cradleVSA.neptune.net.RestApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provide the singleton objects for data access
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Module
@InstallIn(SingletonComponent::class)
class DataModule {
    @Provides
    @Singleton
    fun providePatientManager(
        database: CradleDatabase,
        patientDao: PatientDao,
        readingDao: ReadingDao,
        restApi: RestApi
    ) = PatientManager(database, patientDao, readingDao, restApi)

    @Provides
    @Singleton
    fun provideReadingManager(
        readingDao: ReadingDao,
        restApi: RestApi
    ) = ReadingManager(readingDao, restApi)

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
        return EncryptedSharedPreferencesCustom.create(
            "shared-pref-encrypted",
            context,
            EncryptedSharedPreferencesCustom.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferencesCustom.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun providesHttp(): Http = Http()

    @Provides
    @Singleton
    fun provideRestApi(
        sharedPreferences: SharedPreferences,
        urlManager: UrlManager,
        http: Http
    ) = RestApi(sharedPreferences, urlManager, http)

    @Provides
    @Singleton
    fun provideUrlManager(settings: Settings) = UrlManager(settings)

    @Provides
    @Singleton
    fun provideSettings(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context
    ) = Settings(sharedPreferences, context)
}
