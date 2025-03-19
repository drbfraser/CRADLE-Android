package com.cradleplatform.neptune.di

import android.content.Context
import android.content.SharedPreferences
import com.cradleplatform.neptune.BuildConfig
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.introduction.IntroActivity.Companion.LAST_VERSION_TO_COMPLETE_WIZARD
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
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        val sharedPreferences = context.getSharedPreferences("ui-test", Context.MODE_PRIVATE)

        /** To avoid the "Grant Permissions" intro screen, we need to set
         * `LAST_VERSION_TO_COMPLETE_WIZARD` in shared preferences.
         * We can create a new SharedPreferences to inject so as to avoid polluting the
         * default SharedPreferences. */
        sharedPreferences.edit().putLong(LAST_VERSION_TO_COMPLETE_WIZARD, BuildConfig.VERSION_CODE.toLong()).apply()

        /** To avoid the pop-up about setting a PIN, we can insert a PIN into the
         * SharedPreferences before the tests. */
        val pinCodePrefKey = context.getString(R.string.key_pin_shared_key)
        sharedPreferences.edit().putString(pinCodePrefKey, "0000").apply()

        return sharedPreferences
    }

}