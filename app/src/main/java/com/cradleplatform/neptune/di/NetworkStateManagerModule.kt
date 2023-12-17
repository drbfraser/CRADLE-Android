package com.cradleplatform.neptune.di

import android.content.Context
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkMonitoringUtil
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Provides
    @Singleton
    fun provideNetworkMonitoringUtil(
        @ApplicationContext context: Context,
        networkStateManager: NetworkStateManager,
    ): NetworkMonitoringUtil {
        return NetworkMonitoringUtil(
            context,
            networkStateManager
        )
    }
}
