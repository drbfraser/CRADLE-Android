package com.cradle.neptune.model;

import androidx.test.espresso.ViewAssertion;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.cradle.neptune.R;
import com.cradle.neptune.view.LoginActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class LoginActivityUiTest {

    @Rule
    public ActivityTestRule<LoginActivity> activityTestRule = new ActivityTestRule<>(LoginActivity.class);

    @Test
    public void testInvalidCredentialViewDisplayed(){
        //fill in the email
        onView(withId(R.id.emailEditText)).perform(typeText("a@a.com"));
        //fill in random password and close the keyboard
        onView(withId(R.id.passwordEditText)).perform(closeSoftKeyboard(),click(),typeText("1234"));

        onView(withId(R.id.loginButton)).perform(closeSoftKeyboard(),click());
        //check the error message is displayed
        onView(withId(R.id.invalidLoginText)).check(matches(isDisplayed()));

    }

    @Test
    public void testInvalidCredentialErrorMessage(){
        //fill in the email
        onView(withId(R.id.emailEditText)).perform(typeText("a@a.com"));
        //fill in random password and close the keyboard
        onView(withId(R.id.passwordEditText)).perform(closeSoftKeyboard(),click(),typeText("1234"));

        onView(withId(R.id.loginButton)).perform(closeSoftKeyboard(),click());
        //check the error message is displayed
        onView(withId(R.id.invalidLoginText)).check(matches(withText("Error")));

    }
}
