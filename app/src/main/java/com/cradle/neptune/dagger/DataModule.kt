package com.cradle.neptune.dagger

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.MarshalManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.manager.VolleyRequestManager
import com.cradle.neptune.network.Api
import com.cradle.neptune.network.VolleyRequestQueue
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Provide the singleton objects for data access
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Module
class DataModule {
    @Provides
    @Singleton
    fun provideDatabase(application: Application?): CradleDatabase {
        return Room.databaseBuilder(
            application!!,
            CradleDatabase::class.java,
            "room-readingDB"
        ).build()
    }

    @Provides
    @Singleton
    fun provideReadingService(
        database: CradleDatabase,
        marshalManager: MarshalManager?
    ): ReadingManager {
        return ReadingManager(database.readingDaoAccess())
    }

    @Provides
    @Singleton
    fun provideHealthCentreService(database: CradleDatabase?): HealthCentreManager {
        return HealthCentreManager(database!!)
    }

    @Provides
    @Singleton
    fun providePatientManager(database: CradleDatabase): PatientManager {
        return PatientManager(database.patientDaoAccess())
    }

    @Provides
    @Singleton
    fun providesSharedPreferences(application: Application?): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(application)
    }

    @Provides
    @Singleton
    fun provideVolleyRequestQueue(application: Application?): VolleyRequestQueue {
        return VolleyRequestQueue(application!!)
    }

    @Provides
    @Singleton
    fun provideVolleyRequestManager(application: Application?): VolleyRequestManager {
        return VolleyRequestManager(application!!)
    }

    @Provides
    @Singleton
    fun provideApi(
        sharedPreferences: SharedPreferences?,
        urlManager: UrlManager?,
        volleyRequestQueue: VolleyRequestQueue?
    ): Api {
        return Api(sharedPreferences!!, urlManager!!, volleyRequestQueue!!)
    }
}
