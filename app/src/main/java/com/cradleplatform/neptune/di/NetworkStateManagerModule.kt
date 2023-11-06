package com.cradleplatform.neptune.di

import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkStateManagerModule {
    @Provides
    @Singleton
    fun provideNetworkStateManager(): NetworkStateManager {
        return NetworkStateManager.getInstance()
    }
}