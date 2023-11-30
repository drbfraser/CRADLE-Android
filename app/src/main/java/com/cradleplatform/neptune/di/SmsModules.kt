package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
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
    fun provideSmsSender(
        sharedPreferences: SharedPreferences,
        @ApplicationContext context: Context
    ) = SMSSender(sharedPreferences, context)
}
