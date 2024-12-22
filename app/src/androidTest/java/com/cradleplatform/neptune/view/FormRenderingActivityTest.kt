package com.cradleplatform.neptune.view

import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.VisibleCondition
import com.cradleplatform.neptune.activities.dashboard.DashBoardActivity
import com.cradleplatform.neptune.activities.forms.FormRenderingActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class FormRenderingActivityTest{

    @get:Rule
    var activityScenarioRule = activityScenarioRule<DashBoardActivity>()

    // Takes a list of questions and starts the forms activity with those questions.
    private fun startActivityWithQuestions(questions: List<Question>){
        activityScenarioRule.scenario.onActivity { activity ->
            val intent = FormRenderingActivity.makeIntentWithFormTemplate(
                activity,
                FormTemplate(
                    "test-version",
                    null,
                    null,
                    "test-template",
                    "",
                    null,
                    questions
                ),
                "test-language",
                "test-id",
                Patient("test-patient")
            )
            ContextCompat.startActivity(activity, intent, null)
            val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return@onActivity
            with (sharedPref.edit()) {
                putString(LoginManager.RELAY_PHONE_NUMBER, "+1-555-521-5556")
                apply()
            }

        }
    }
    @Test
    fun test_Categories(){
        val questions = mutableListOf<Question>()
        val visibleCondition = listOf(VisibleCondition(null, null, null))
        // Create 10 categories and questions to test
        // Note that the 10th category has no questions so it must not appear
        for (i in 1..10) {
            val stringQuestion = Question(
                id = "Q$i",
                visibleCondition = visibleCondition,
                isBlank = true,
                formTemplateId = null,
                questionIndex = 0,
                numMin = null,
                numMax = null,
                stringMaxLength = null,
                stringMaxLines = null,
                questionId = "Q$i",
                questionType = QuestionTypeEnum.STRING,
                hasCommentAttached = false,
                required = false,
                languageVersions = listOf(QuestionLangVersion("test-language",
                    "test-parent","Test $i",i,null))
            )
            val categoryQuestion = Question(
                id = "C$i",
                visibleCondition = visibleCondition,
                isBlank = true,
                formTemplateId = null,
                questionIndex = 0,
                numMin = null,
                numMax = null,
                stringMaxLength = null,
                stringMaxLines = null,
                questionId = "C$i",
                questionType = QuestionTypeEnum.CATEGORY,
                hasCommentAttached = false,
                required = false,
                languageVersions = listOf(QuestionLangVersion("test-language",
                    "test-parent","Category #$i",i,null))
            )
            questions.add(stringQuestion)
            questions.add(categoryQuestion)
        }

        // Starts the activity with the given questions
        startActivityWithQuestions(questions)

        // Returns the current activity of the UI
        // Source: https://stackoverflow.com/a/58684943
        fun getCurrentActivity(): Activity? {
            var currentActivity: Activity? = null
            getInstrumentation().runOnMainSync { run { currentActivity =
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(
                Stage.RESUMED).elementAtOrNull(0) } }
            return currentActivity
        }

        // Takes the name of a category, clicks its button and verifies that the correct name
        // is visible in the title
        fun testCategoryButton(categoryName : String){
            onView(withId(R.id.form_state_button)).perform(click())
            onView(withText(categoryName)).perform(scrollTo()).check(matches(isDisplayed()))
                .perform(click())
            assert(getCurrentActivity()?.title.toString() == categoryName)
        }

        // Fill the answers to 9 questions and check that all the questions are appearing correctly.
        // And test the category button for categories 1-9
        for (i in 1..9) {
            onView(withId(R.id.tv_question)).check(matches(withText("1. Test $i")))
            onView(withId(R.id.et_answer)).perform(typeText("Answer $i"))
            closeSoftKeyboard()
            testCategoryButton("Category #$i")
        }

        // Test uncategorized button and verify that the answer persists after switching categories
        testCategoryButton("Uncategorized")
        onView(withId(R.id.et_answer)).check(matches(withText("Answer 1")))

        // Verify that there is no Category 10
        onView(withId(R.id.form_state_button)).perform(click())
        onView(withText("Category #10")).check(doesNotExist())

        // Verify that the next button moves categories appropriately
        for (i in 1..9) {
            onView(withId(R.id.form_next_category_button)).perform(click())
            assert(getCurrentActivity()?.title.toString() == "Category #$i")
        }
        onView(withId(R.id.form_next_category_button)).perform(click())
        assert(getCurrentActivity()?.title.toString() == "Category #9")

        // Verify that the prev button moves categories appropriately
        for (i in 9 downTo 1) {
            assert(getCurrentActivity()?.title.toString() == "Category #$i")
            onView(withId(R.id.form_prev_category_button)).perform(click())
        }
        assert(getCurrentActivity()?.title.toString() == "Uncategorized")
        onView(withId(R.id.form_prev_category_button)).perform(click())
        assert(getCurrentActivity()?.title.toString() == "Uncategorized")

        // Verify that submit options appear correctly
        onView(withId(R.id.formSubmit)).perform(click())
        onView(withText("SMS")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Wifi")).perform(scrollTo()).check(matches(isDisplayed()))
    }
}