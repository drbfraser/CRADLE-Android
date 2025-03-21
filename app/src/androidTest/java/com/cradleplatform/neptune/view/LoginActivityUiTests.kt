package com.cradleplatform.neptune.view

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.authentication.LoginActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class LoginActivityUiTests {
    // https://developer.android.com/guide/components/activities/testing
    @get:Rule
    var activityScenarioRule = activityScenarioRule<LoginActivity>()

    private lateinit var idlingResource: IdlingResource

    @Before
    fun before() {
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