package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.http_sms_service.sms.SMSReceiver
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.SmsKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SmsModules {
    @Provides
    @Singleton
    fun provideSmsKeyManager(@ApplicationContext context: Context): SmsKeyManager {
        return SmsKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideSmsStateReporter(
        smsKeyManager: SmsKeyManager,
        @DataModule.EncryptedPrefs encryptedPreferences: SharedPreferences
    ): SmsStateReporter {
        return SmsStateReporter(smsKeyManager, encryptedPreferences)
    }

    @Provides
    @Singleton
    fun provideSMSDataProcessor(
        smsStateReporter: SmsStateReporter
    ) = SMSDataProcessor(smsStateReporter)

    @Provides
    @Singleton
    fun provideSmsSender(
        smsKeyManager: SmsKeyManager,
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
        smsStateReporter: SmsStateReporter,
    ) = SMSSender(smsKeyManager, sharedPreferences, context, smsStateReporter)

    @Provides
    @Singleton
    fun provideSmsReceiver(
        @ApplicationContext context: Context,
        sharedPreferences: SharedPreferences,
        smsSender: SMSSender,
        smsStateReporter: SmsStateReporter,
    ) = SMSReceiver(context, sharedPreferences, smsSender, smsStateReporter)
}
