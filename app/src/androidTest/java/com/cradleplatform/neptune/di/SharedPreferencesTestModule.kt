package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.activities.introduction.IntroActivity.Companion.LAST_VERSION_TO_COMPLETE_WIZARD
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** This module will replace the normal SharedPreferencesModule, thus allowing us to use a separate
 * SharedPreferences for testing.
 * */

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SharedPreferencesModule::class]
)
class SharedPreferencesTestModule {
    @Singleton
    @Provides
    fun bindSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        /** To avoid the "Grant Permissions" intro screen, we need to set
         * `LAST_VERSION_TO_COMPLETE_WIZARD` in shared preferences.
         * We can create a new SharedPreferences to inject so as to avoid polluting the
         * default SharedPreferences. */
        val sharedPreferences = context.getSharedPreferences("ui-test", Context.MODE_PRIVATE)
        sharedPreferences.edit().putLong(LAST_VERSION_TO_COMPLETE_WIZARD, BuildConfig.VERSION_CODE.toLong()).apply()
        return sharedPreferences
    }
}