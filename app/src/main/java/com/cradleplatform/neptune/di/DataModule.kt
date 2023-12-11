package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.database.daos.AssessmentDao
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.database.daos.FormResponseDao
import com.cradleplatform.neptune.database.daos.HealthFacilityDao
import com.cradleplatform.neptune.database.daos.PatientDao
import com.cradleplatform.neptune.database.daos.ReadingDao
import com.cradleplatform.neptune.database.daos.ReferralDao
import com.cradleplatform.neptune.http_sms_service.http.Http
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Settings
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
        database: CradleDatabase,
        readingDao: ReadingDao,
        restApi: RestApi
    ) = ReadingManager(database, readingDao, restApi)

    @Provides
    @Singleton
    fun provideReferralManager(
        database: CradleDatabase,
        referralDao: ReferralDao,
        restApi: RestApi
    ) = ReferralManager(database, referralDao, restApi)

    @Provides
    @Singleton
    fun provideAssessmentManager(
        database: CradleDatabase,
        assessmentDao: AssessmentDao,
        restApi: RestApi
    ) = AssessmentManager(database, assessmentDao, restApi)

    @Provides
    @Singleton
    fun provideFormManager(
        restApi: RestApi,
        formClassificationDao: FormClassificationDao
    ) = FormManager(restApi, formClassificationDao)

    @Provides
    @Singleton
    fun provideFormResponseManager(
        restApi: RestApi,
        formResponseDao: FormResponseDao
    ) = FormResponseManager(restApi, formResponseDao)

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
    fun provideReferralDao(database: CradleDatabase): ReferralDao =
        database.referralDao()

    @Provides
    fun provideAssessmentDao(database: CradleDatabase): AssessmentDao =
        database.assessmentDao()

    @Provides
    fun provideFormClassificationDao(database: CradleDatabase): FormClassificationDao =
        database.formClassificationDao()

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
