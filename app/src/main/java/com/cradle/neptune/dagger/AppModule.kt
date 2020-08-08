package com.cradle.neptune.dagger

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDexApplication
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Allow access to the Application
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Module
class AppModule(var application: MultiDexApplication) {
    @Provides
    @Singleton
    fun providesApplication(): Application {
        return application
    }

    @Provides
    @Singleton
    fun providesContext(): Context {
        return application
    }
}
