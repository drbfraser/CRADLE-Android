package com.cradle.neptune.view

import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.cradle.neptune.R
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.core.IsInstanceOf

/**
 * ref: https://medium.com/android-bits/espresso-robot-pattern-in-kotlin-fc820ce250f7
 */
class ReadingActivityTestHelper {
    /**
     * Enters [textToEnter] into a Material Design TextInputEditTExt with id [viewIdRes], and if
     * [closeKeyboardAfter] is true (it's true by default), it will close the keyboard.
     * [eraseBeforeTyping] (false by default) erases the EditText before typing. If not true and
     * there's already text in it, it will add on to the text present.
     */
    fun typeTextInMaterialTextField(
        @IdRes viewIdRes: Int,
        textToEnter: String,
        eraseBeforeTyping: Boolean = false,
        closeKeyboardAfter: Boolean = true
    ) {
        onView(withId(viewIdRes)).apply {
            perform(descendantScrollTo())
            // Need to click the layout in order to get the InputEditText
            perform(click())
            if (eraseBeforeTyping || textToEnter.isEmpty()) {
                perform(replaceText(""))
            }
            perform(typeText(textToEnter))
            if (closeKeyboardAfter) {
                perform(closeSoftKeyboard())
            }
        }
    }

    /**
     * For an AutoCompleteTextView used as a Spinner with a resource ID of [spinnerIdRes],
     * selects the item that has [itemString] for its text.
     */
    fun selectMaterialSpinnerItem(@IdRes spinnerIdRes: Int, itemString: String) {
        onView(withId(spinnerIdRes))
            .perform(descendantScrollTo())
            .perform(click())
        onView(withText(itemString))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())
    }

    /**
     * Checks that an error text from a TextInputLayout
     */
    fun checkErrorText(@StringRes errorTextResId: Int) {
        val errorHelperTextView = onView(
            allOf(
                // This was automatically generated by the test recorder.
                // To get this ID again, use Android Studio's Layout Inspector
                withId(R.id.textinput_error),
                withText(errorTextResId),
                withParent(withParent(IsInstanceOf.instanceOf(LinearLayout::class.java))),
                isDisplayed()
            )
        )
        errorHelperTextView.check(matches(withText(errorTextResId)))
    }

    fun checkEnabled(@IdRes viewResId: Int, shouldBeEnabled: Boolean) {
        if (shouldBeEnabled) {
            onView(withId(viewResId)).check(matches(isEnabled()))
        } else {
            onView(withId(viewResId)).check(matches(not(isEnabled())))
        }
    }

    fun clickIfEnabled(@IdRes viewResId: Int, doScrollTo: Boolean = true) {
        onView(withId(viewResId)).apply {
            if (doScrollTo) {
                perform(descendantScrollTo())
            }
            check(matches(isEnabled()))
            perform(click())
        }
    }

    fun clickCheckboxWithText(checkboxText: String) {
        onView(withText(checkboxText)).perform(click())
    }
}

fun readingActivityTestHelper(block: ReadingActivityTestHelper.() -> Unit) =
    ReadingActivityTestHelper().apply { block() }