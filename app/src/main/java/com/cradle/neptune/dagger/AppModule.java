package com.cradle.neptune.dagger;

import android.app.Application;

import androidx.multidex.MultiDexApplication;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Allow access to the Application
 * source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Module
public class AppModule {
    MultiDexApplication application;

    public AppModule(MultiDexApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    Application providesApplication() {
        return application;
    }
}
