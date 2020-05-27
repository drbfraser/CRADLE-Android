package com.cradle.neptune.dagger;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.room.Room;

import com.cradle.neptune.database.CradleDatabase;
import com.cradle.neptune.model.Settings;

import javax.inject.Singleton;

import com.cradle.neptune.service.HealthCentreService;
import com.cradle.neptune.service.MarshalService;
import com.cradle.neptune.service.ReadingService;
import com.cradle.neptune.service.impl.HealthCentreServiceImpl;
import com.cradle.neptune.service.impl.ReadingServiceImpl;
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
    public ReadingService provideReadingService(CradleDatabase database, MarshalService marshalService) {
        return new ReadingServiceImpl(database, marshalService);
    }

    @Provides
    @Singleton
    public HealthCentreService provideHealthCentreService(CradleDatabase database) {
        return new HealthCentreServiceImpl(database);
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
