package com.cradle.neptune.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ScrollToAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.cradle.neptune.R
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.hamcrest.core.IsInstanceOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReadingActivityUiTests {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // https://developer.android.com/guide/components/activities/testing
    @get:Rule
    var activityScenarioRule = activityScenarioRule<DashBoardActivity>()

    @Rule
    @JvmField
    val mGrantPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )

    @Before
    fun before() {
        activityScenarioRule.scenario.onActivity { activity ->
            val intent = ReadingActivity.makeIntentForNewReading(activity)
            startActivity(activity, intent, null)
        }
    }

    @Test
    fun readingActivity_addBadPatientInfo() {
        onView(withId(R.id.patient_name_text))
            .perform(click())
            .perform(replaceText("baad3"), closeSoftKeyboard())

        val textView = onView(
            allOf(
                withId(R.id.textinput_error), withText("Name must be characters"),
                withParent(withParent(IsInstanceOf.instanceOf(android.widget.LinearLayout::class.java))),
                isDisplayed()
            )
        )
        textView.check(matches(withText("Name must be characters")))


        val currentTime = System.currentTimeMillis() / 1000L
        onView(withId(R.id.patient_id_text))
            .perform(click())
            .perform(replaceText(currentTime.toString()), closeSoftKeyboard())
        Thread.sleep(1000L)
        onView(withId(R.id.patient_id_text))
            .perform(click())
            .perform(replaceText(""), closeSoftKeyboard())
        val textView2 = onView(
            allOf(
                withId(R.id.textinput_error), withText("Missing patient ID"),
                withParent(withParent(IsInstanceOf.instanceOf(android.widget.LinearLayout::class.java))),
                isDisplayed()
            )
        )
        textView2.check(matches(withText("Missing patient ID")))

        onView(withId(R.id.reading_next_button)).check(matches(not(isEnabled())))
    }

    @Test
    fun readingActivity_addGoodPatientInfo() {
        onView(withId(R.id.patient_name_text))
            .perform(click())
            .perform(typeText("John Smith"), closeSoftKeyboard())
        val currentTime = System.currentTimeMillis() / 1000L
        onView(withId(R.id.patient_id_text))
            .perform(click())
            .perform(replaceText(currentTime.toString()), closeSoftKeyboard())
        onView(withId(R.id.village_text))
            .perform(click())
            .perform(replaceText("567"), closeSoftKeyboard())
        onView(withId(R.id.age_input_text))
            .perform(click())
            .perform(replaceText("32"), closeSoftKeyboard())

        onView(withId(R.id.reading_next_button)).check(matches(not(isEnabled())))

        onView(withId(R.id.gender_input_auto_complete_text))
            .perform(click())

        val otherString: String = context.resources.getStringArray(R.array.sex)[2]
        onView(withText(otherString))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        Thread.sleep(2000L)

        onView(withId(R.id.reading_next_button))
            .check(matches(isEnabled()))
            .perform(click())
        Thread.sleep(2500L)

        val feverish: String = context.resources.getStringArray(R.array.reading_symptoms)[5]
        onView(withText(feverish))
            .perform(click())

        onView(withId(R.id.reading_next_button))
            .check(matches(isEnabled()))
            .perform(click())
        Thread.sleep(2500L)

        onView(withId(R.id.systolic_text))
            .perform(descendantScrollTo())
            .perform(click())
            .perform(replaceText("114"))
        onView(withId(R.id.diastolic_text))
            .perform(descendantScrollTo())
            .perform(click())
            .perform(replaceText("89"))
        onView(withId(R.id.heart_rate_text))
            .perform(descendantScrollTo())
            .perform(click())
            .perform(replaceText("87"), closeSoftKeyboard())

        onView(withId(R.id.blood_auto_complete_text))
            .perform(descendantScrollTo(), click())
        onView(withText("NAD"))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        onView(withId(R.id.protein_auto_complete_text))
            .perform(descendantScrollTo(), click())
        onView(withText("NAD"))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        onView(withId(R.id.glucose_auto_complete_text))
            .perform(descendantScrollTo(), click())
        onView(withText("NAD"))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        onView(withId(R.id.nitrites_auto_complete_text))
            .perform(descendantScrollTo(), click())
        onView(withText("NAD"))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        onView(withId(R.id.health_facility_auto_complete_text))
            .perform(descendantScrollTo(), click())
        onView(withText("NAD"))
            .inRoot(RootMatchers.isPlatformPopup())
            .perform(click())

        Thread.sleep(500L)
        onView(withId(R.id.reading_next_button))
            .check(matches(isEnabled()))
            .perform(click())

        onView(withId(R.id.save_reading_button))
            .perform(descendantScrollTo(), click())

        onView(withId(R.id.patientCardview)).perform(scrollTo(), click())

        onView(withId(R.id.patientListRecyclerview))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click())
            )

        onView(withId(R.id.patientName))
            .check(matches(withText("John Smith")))
        onView(withId(R.id.patientId))
            .check(matches(withText(currentTime.toString())))
        onView(withId(R.id.patientVillage))
            .check(matches(withText("567")))
        onView(withId(R.id.patientAge))
            .check(matches(withText("About 32 years old")))
    }

    /**
     * Generated by test recorder
     */
    private fun childAtPosition(
        parentMatcher: Matcher<View>, position: Int
    ): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                    && view == parent.getChildAt(position)
            }
        }
    }
}

// https://stackoverflow.com/a/64750973
class DescendantScrollToAction : ViewAction by ScrollToAction() {
    override fun getConstraints(): Matcher<View> {
        return allOf(
            withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
            isDescendantOfA(
                anyOf(
                    isAssignableFrom(ScrollView::class.java),
                    isAssignableFrom(HorizontalScrollView::class.java),
                    isAssignableFrom(NestedScrollView::class.java)
                )
            )
        )
    }
}

// convenience method
fun descendantScrollTo(): ViewAction = ViewActions.actionWithAssertions(DescendantScrollToAction())
