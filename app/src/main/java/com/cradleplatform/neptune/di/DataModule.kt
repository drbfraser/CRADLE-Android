package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
import com.cradleplatform.neptune.http_sms_service.sms.SMSReceiver
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.FormManager
import com.cradleplatform.neptune.manager.FormResponseManager
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.ReferralUploadManager
import com.cradleplatform.neptune.manager.UrlManager
import com.cradleplatform.neptune.model.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
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
        formResponseDao: FormResponseDao,
        formClassificationDao: FormClassificationDao
    ) = FormResponseManager(formResponseDao, formClassificationDao)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context) = CradleDatabase.getInstance(context)

    @Provides
    fun providePatientDao(database: CradleDatabase): PatientDao = database.patientDao()

    @Provides
    fun provideReadingDao(database: CradleDatabase): ReadingDao = database.readingDao()

    @Provides
    fun provideHealthFacilityDao(database: CradleDatabase): HealthFacilityDao =
        database.healthFacility()

    @Provides
    fun provideReferralDao(database: CradleDatabase): ReferralDao = database.referralDao()

    @Provides
    fun provideAssessmentDao(database: CradleDatabase): AssessmentDao = database.assessmentDao()

    @Provides
    fun provideFormClassificationDao(database: CradleDatabase): FormClassificationDao =
        database.formClassificationDao()

    @Provides
    fun provideFormResponseDao(database: CradleDatabase): FormResponseDao =
        database.formResponseDao()

    @Provides
    @Singleton
    fun provideHealthCentreService(database: CradleDatabase): HealthFacilityManager {
        return HealthFacilityManager(database)
    }

    @Provides
    @Singleton
    @EncryptedPrefs
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val masterKey: MasterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class EncryptedPrefs

    @Provides
    @Singleton
    fun providesHttp(
        sharedPreferences: SharedPreferences
    ): Http = Http(sharedPreferences)

    @Provides
    @Singleton
    fun provideRestApi(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
        urlManager: UrlManager,
        http: Http,
        smsStateReporter: SmsStateReporter,
        smsSender: SMSSender,
        smsReceiver: SMSReceiver,
        smsDataProcessor: SMSDataProcessor,
    ) = RestApi(
        context,
        sharedPreferences,
        urlManager,
        http,
        smsStateReporter,
        smsSender,
        smsReceiver,
        smsDataProcessor
    )

    @Provides
    @Singleton
    fun provideUrlManager(settings: Settings) = UrlManager(settings)

    @Provides
    @Singleton
    fun provideSettings(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context
    ) = Settings(sharedPreferences, context)

    @Provides
    @Singleton
    fun provideReferralUploadManager(
        restApi: RestApi,
        referralManager: ReferralManager,
        patientManager: PatientManager
    ) = ReferralUploadManager(restApi, referralManager, patientManager)
}
