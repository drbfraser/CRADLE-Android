package com.cradle.neptune.dagger;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.room.Room;

import com.cradle.neptune.database.CradleDatabase;
import com.cradle.neptune.model.Settings;

import javax.inject.Singleton;

import com.cradle.neptune.manager.HealthCentreManager;
import com.cradle.neptune.manager.MarshalManager;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.manager.impl.HealthCentreManagerImpl;
import com.cradle.neptune.manager.impl.ReadingManagerImpl;
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
    public CradleDatabase provideDatabase(Application application) {
        return Room.databaseBuilder(application, CradleDatabase.class, "room-readingDB")
                .allowMainThreadQueries()
                .build();
    }

    @Provides
    @Singleton
    public ReadingManager provideReadingService(CradleDatabase database, MarshalManager marshalManager) {
        return new ReadingManagerImpl(database, marshalManager);
    }

    @Provides
    @Singleton
    public HealthCentreManager provideHealthCentreService(CradleDatabase database) {
        return new HealthCentreManagerImpl(database);
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
