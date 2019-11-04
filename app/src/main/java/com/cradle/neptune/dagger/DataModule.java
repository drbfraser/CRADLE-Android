package com.cradle.neptune.dagger;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.ReadingManagerAsDB;
import com.cradle.neptune.model.Settings;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Provide the singleton objects for data access
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Module
public class DataModule {
    @Provides
    @Singleton
    public ReadingManager provideReadingManager() {
//        return new ReadingManagerAsList();
        return new ReadingManagerAsDB();
    }

    @Provides
    @Singleton
    public SharedPreferences providesSharedPreferences(Application application) {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    @Provides
    @Singleton
    public Settings providesSettings(Application application) {
        return new Settings(providesSharedPreferences(application));
    }
}
