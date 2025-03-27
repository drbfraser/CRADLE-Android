package com.cradleplatform.neptune.testutils

import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

class TestUtils {
    companion object {
        fun getCurrentActivity(): Activity? {
            var currentActivity: Activity? = null
            getInstrumentation().runOnMainSync {
                run {
                    currentActivity = ActivityLifecycleMonitorRegistry
                        .getInstance()
                        .getActivitiesInStage(Stage.RESUMED).elementAtOrNull(0)
                }
            }
            return currentActivity
        }
    }
}