package com.cradle.neptune.model;

import android.content.res.Resources;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.cradle.neptune.R;
import com.cradle.neptune.view.LoginActivity;

import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
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
        onView(withId(R.id.invalidLoginText)).check(matches(withText(R.string.login_error)));

    }


    public static Matcher<View> withText(final int resourceId) {

        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("with string from resource id: ");
                description.appendValue(resourceId);
                if (null != this.resourceName) {
                    description.appendText("[");
                    description.appendText(this.resourceName);
                    description.appendText("]");
                }
                if (null != this.expectedText) {
                    description.appendText(" value: ");
                    description.appendText(this.expectedText);
                }
            }

            private String resourceName = null;
            private String expectedText = null;


            @Override
            public boolean matchesSafely(TextView textView) {
                if (null == this.expectedText) {
                    try {
                        this.expectedText = textView.getResources().getString(
                                resourceId);
                        this.resourceName = textView.getResources()
                                .getResourceEntryName(resourceId);
                    } catch (Resources.NotFoundException ignored) {
                        /*
                         * view could be from a context unaware of the resource
                         * id.
                         */
                    }
                }
                if (null != this.expectedText) {
                    return this.expectedText.equals(textView.getText()
                            .toString());
                } else {
                    return false;
                }
            }
        };
    }
}
