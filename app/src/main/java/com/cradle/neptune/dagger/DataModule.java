package com.cradle.neptune.dagger;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.room.Room;

import com.cradle.neptune.database.CradleDatabase;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.RoomDatabaseManager;
import com.cradle.neptune.model.Settings;

import javax.inject.Singleton;

import com.cradle.neptune.service.HealthCentreService;
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

    private CradleDatabase database = null;

    private CradleDatabase lazyInitDatabase(Application application) {
        if (database == null) {
            database = Room.databaseBuilder(application, CradleDatabase.class, "room-readingDB")
                    .allowMainThreadQueries()
                    .build();
        }
        return database;
    }

    @Provides
    @Singleton
    public ReadingManager provideReadingManager(Application application) {
//        return new ReadingManagerAsList();
        //allowing queries on main thread but should use a seperate thread for large queeries
        return new RoomDatabaseManager(lazyInitDatabase(application));
    }

    @Provides
    @Singleton
    public ReadingService provideReadingService(Application application) {
        return new ReadingServiceImpl(lazyInitDatabase(application));
    }

    @Provides
    @Singleton
    public HealthCentreService provideHealthCentreService(Application application) {
        return new HealthCentreServiceImpl(lazyInitDatabase(application));
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
