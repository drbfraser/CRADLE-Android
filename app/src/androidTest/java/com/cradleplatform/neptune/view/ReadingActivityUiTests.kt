package com.cradleplatform.neptune.view

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ScrollToAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.activities.dashboard.DashBoardActivity
import com.cradleplatform.neptune.activities.newPatient.ReadingActivity
import com.cradleplatform.neptune.testutils.rules.GrantRuntimePermissionsRule
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4::class)
class ReadingActivityUiTests {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // https://developer.android.com/training/dependency-injection/hilt-testing#ui-test
    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    // https://developer.android.com/guide/components/activities/testing
    @get:Rule(order = 1)
    var activityScenarioRule = activityScenarioRule<DashBoardActivity>()

    @get:Rule(order = 1)
    var grantPermissionRule = GrantRuntimePermissionsRule()

    private lateinit var idlingResource: IdlingResource

    private var currentActivity: Activity? = null

    companion object {
        const val TAG = "ReadingActivityUiTests"
    }

    @Before
    fun before() {
        AndroidThreeTen.init(context);
        val lock = ReentrantLock()
        val isReadingActivityCondition = lock.newCondition()

        activityScenarioRule.scenario.onActivity { activity ->
            activity.application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: Activity) {
                        lock.withLock {
                            currentActivity = activity
                            isReadingActivityCondition.signal()
                        }
                    }
                    override fun onActivityCreated(activity: Activity, savedState: Bundle?) {
                    }
                    override fun onActivityStarted(activity: Activity) {
                    }
                    override fun onActivityPaused(activity: Activity) {
                    }
                    override fun onActivityStopped(activity: Activity) {
                    }
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    }
                    override fun onActivityDestroyed(activity: Activity) {
                    }
            })

            val intent = ReadingActivity.makeIntentForNewReading(activity)
            startActivity(activity, intent, null)
        }

        lock.withLock {
            while (currentActivity == null) {
                isReadingActivityCondition.await()
            }
        }
        idlingResource = (currentActivity as ReadingActivity).getIdlingResource()
        IdlingRegistry.getInstance().register(idlingResource)
    }

    @After
    fun after() {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }

    @Test
    fun readingActivity_addBadPatientInfo() {
        readingActivityTestHelper {
            typeTextInMaterialTextField(
                viewIdRes = R.id.patient_name_text,
                textToEnter = "baad3",
                closeKeyboardAfter = true
            )
            checkErrorText(R.string.patient_error_name_must_be_characters)

            val currentTime = UnixTimestamp.now
            typeTextInMaterialTextField(
                viewIdRes = R.id.patient_id_text,
                textToEnter = currentTime.toString(),
                closeKeyboardAfter = true
            )
            
            typeTextInMaterialTextField(
                viewIdRes = R.id.patient_id_text,
                textToEnter = ""
            )
            
            checkErrorText(R.string.patient_error_id_missing)
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)
        }
    }

    @Test
    fun readingActivity_addGoodPatientInfo() {
        val currentTime = System.currentTimeMillis() / 1000L

        readingActivityTestHelper {
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)

            typeTextInMaterialTextField(
                viewIdRes = R.id.patient_name_text,
                textToEnter = "John Smith",
                eraseBeforeTyping = false,
                closeKeyboardAfter = true
            )
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)

            typeTextInMaterialTextField(
                viewIdRes = R.id.patient_id_text,
                textToEnter = currentTime.toString()
            )
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)

            typeTextInMaterialTextField(
                viewIdRes = R.id.village_text,
                textToEnter = "567"
            )
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)

            typeTextInMaterialTextField(
                viewIdRes = R.id.age_input_text,
                textToEnter = "32"
            )
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = false)

            selectMaterialSpinnerItem(
                R.id.gender_input_auto_complete_text,
                context.resources.getStringArray(R.array.sex)[2] // Gender: Other
            )

            
            checkEnabled(R.id.reading_next_button, shouldBeEnabled = true)
            clickIfEnabled(R.id.reading_next_button, doScrollTo = false)

            clickCheckboxWithText(context.resources.getStringArray(R.array.reading_symptoms)[5])

            clickIfEnabled(R.id.reading_next_button, doScrollTo = false)
            

            typeTextInMaterialTextField(
                viewIdRes = R.id.systolic_text,
                textToEnter = "114"
            )
            typeTextInMaterialTextField(
                viewIdRes = R.id.diastolic_text,
                textToEnter = "89"
            )
            typeTextInMaterialTextField(
                viewIdRes = R.id.heart_rate_text,
                textToEnter = "87"
            )

            selectMaterialSpinnerItem(
                R.id.blood_auto_complete_text,
                "NAD"
            )
            selectMaterialSpinnerItem(
                R.id.protein_auto_complete_text,
                "NAD"
            )
            selectMaterialSpinnerItem(
                R.id.glucose_auto_complete_text,
                "NAD"
            )
            selectMaterialSpinnerItem(
                R.id.nitrites_auto_complete_text,
                "NAD"
            )
            selectMaterialSpinnerItem(
                R.id.leukocytes_auto_complete_text,
                "NAD"
            )

            clickIfEnabled(R.id.reading_next_button, doScrollTo = false)

            clickIfEnabled(R.id.save_reading_button)
        }

        onView(withId(R.id.patientCardView)).perform(scrollTo(), click())

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
            .check(matches(withText(context.getString(R.string.patient_profile_age_about_n_years_old, 32))))
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
