package com.cradleplatform.neptune.view

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.authentication.LoginActivity
import com.cradleplatform.neptune.activities.dashboard.DashBoardActivity
import com.cradleplatform.neptune.activities.introduction.IntroActivity.Companion.LAST_VERSION_TO_COMPLETE_WIZARD
import com.cradleplatform.neptune.di.SharedPreferencesModule
import com.cradleplatform.neptune.testutils.grantPermissions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Singleton

@UninstallModules(SharedPreferencesModule::class)
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginActivityUiTests {

    @Module
    @InstallIn(SingletonComponent::class)
    class TestModule {
        @Singleton
        @Provides
        fun providesSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
            /** To avoid the "Grant Permissions" intro screen, we need to set
             * `LAST_VERSION_TO_COMPLETE_WIZARD` in shared preferences.
             * We can create a new SharedPreferences to inject so as to avoid polluting the
             * default SharedPreferences. */
            val sharedPreferences = context.getSharedPreferences("ui-test", Context.MODE_PRIVATE)
            sharedPreferences.edit().putLong(LAST_VERSION_TO_COMPLETE_WIZARD, 1)
            return sharedPreferences
        }
    }

    // https://developer.android.com/training/dependency-injection/hilt-testing#ui-test
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    // https://developer.android.com/guide/components/activities/testing
    @get:Rule(order = 1)
    var activityScenarioRule = activityScenarioRule<LoginActivity>()

    private lateinit var idlingResource: IdlingResource

    @Rule
    @JvmField
    val mGrantPermissionRule: GrantPermissionRule = grantPermissions()


    @Before
    fun before() {
        Intents.init()
        activityScenarioRule.scenario.onActivity { activity ->
            idlingResource = activity.getIdlingResource()
            IdlingRegistry.getInstance().register(idlingResource)
        }
    }

    @After
    fun after() {
        if (this::idlingResource.isInitialized) {
            IdlingRegistry.getInstance().unregister(idlingResource)
        }
        Intents.release()
    }

    @Test
    fun loginActivity_successfulLogin() {
        /** To avoid the "Grant Permissions" intro screen, we need to set
         * `LAST_VERSION_TO_COMPLETE_WIZARD` in shared prefs. */
        onView(withId(R.id.emailEditText))
            .perform(click())
            .perform(typeText("admin"))
            .perform(pressImeActionButton())

        onView(withId(R.id.passwordEditText))
            .perform(typeTextIntoFocusedView("cradle-admin"))
            .perform(pressImeActionButton())

        onView(withId(R.id.loginButton))
            .perform(click())

        intended(hasComponent(DashBoardActivity::class.java.name))
    }

    @Test
    fun loginActivity_invalidUsernamePassword() {
        onView(withId(R.id.emailEditText))
            .perform(click())
            .perform(typeText("example@idontexist.com"))
            .perform(pressImeActionButton())

        onView(withId(R.id.passwordEditText))
            .perform(typeTextIntoFocusedView("this password has to be long enough"))
            .perform(pressImeActionButton())

        onView(withId(R.id.loginButton))
            .perform(click())

        onView(withId(R.id.invalidLoginText))
            .check(matches(withText(R.string.login_error)))
    }

}