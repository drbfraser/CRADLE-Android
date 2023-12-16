package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
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
    ): SmsStateReporter {
        return SmsStateReporter(smsKeyManager)
    }

    @Provides
    @Singleton
    fun provideSmsSender(
        smsKeyManager: SmsKeyManager,
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context,
        smsStateReporter: SmsStateReporter,
    ) = SMSSender(smsKeyManager, sharedPreferences, context, smsStateReporter)

}
