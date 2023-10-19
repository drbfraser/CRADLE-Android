package com.cradleplatform.neptune.view

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Question
import com.cradleplatform.neptune.model.QuestionLangVersion
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.VisibleCondition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

@RunWith(AndroidJUnit4ClassRunner::class)
class FormRenderingActivityTest{

    @get:Rule
    var activityScenarioRule = activityScenarioRule<DashBoardActivity>()

    private fun startActivityWithQuestions(questions: List<Question>){
        activityScenarioRule.scenario.onActivity { activity ->
            val intent = FormRenderingActivity.makeIntentWithFormTemplate(
                activity,
                FormTemplate("test-version",null,null,
                         "test-template","",questions),
                "test-language",
                "test-id",
                Patient("test-patient")
            )
            ContextCompat.startActivity(activity, intent, null)
        }
    }
    @Test
    fun test_Categories(){
        val questions = mutableListOf<Question>()
        for (i in 1..10) {
            val stringQuestion = Question(
                id = "Q$i",
                visibleCondition = listOf(VisibleCondition(null, null, null)),
                isBlank = true,
                formTemplateId = null,
                questionIndex = 0,
                numMin = null,
                numMax = null,
                stringMaxLength = null,
                questionId = "Q$i",
                questionType = QuestionTypeEnum.STRING,
                hasCommentAttached = false,
                required = false,
                languageVersions = listOf(QuestionLangVersion("test-language", "test-parent","Test $i",i,null))
            )
            val categoryQuestion = Question(
                id = "C$i",
                visibleCondition = listOf(VisibleCondition(null, null, null)),
                isBlank = true,
                formTemplateId = null,
                questionIndex = 0,
                numMin = null,
                numMax = null,
                stringMaxLength = null,
                questionId = "C$i",
                questionType = QuestionTypeEnum.CATEGORY,
                hasCommentAttached = false,
                required = false,
                languageVersions = listOf(QuestionLangVersion("test-language", "test-parent","Category #$i",i,null))
            )
            questions.add(stringQuestion)
            questions.add(categoryQuestion)
        }

        startActivityWithQuestions(questions)

        // Source: https://stackoverflow.com/a/58684943
        fun getCurrentActivity(): Activity? {
            var currentActivity: Activity? = null
            getInstrumentation().runOnMainSync { run { currentActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(
                Stage.RESUMED).elementAtOrNull(0) } }
            return currentActivity
        }

        fun testCategoryButton(categoryName : String){
            onView(withId(R.id.form_state_button)).perform(click())
            onView(withText(categoryName)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
            assert(getCurrentActivity()?.title.toString() == categoryName)
        }

        for (i in 1..9) {
            onView(withId(R.id.tv_question)).check(matches(withText("1. Test $i")))
            onView(withId(R.id.et_answer)).perform(typeText("Answer $i"))
            closeSoftKeyboard()
            testCategoryButton("Category #$i")
        }

        testCategoryButton("Uncategorized")
        onView(withId(R.id.et_answer)).check(matches(withText("Answer 1")))

        onView(withId(R.id.form_state_button)).perform(click())
        onView(withText("Category #10")).check(doesNotExist())

        onView(withId(R.id.formSubmit)).perform(click())
        onView(withText("SMS")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Wifi")).perform(scrollTo()).check(matches(isDisplayed()))
    }
}