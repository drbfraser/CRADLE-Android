package com.cradleplatform.neptune.testutils

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** This custom test runner is needed for using Hilt in UI tests.
 * https://developer.android.com/training/dependency-injection/hilt-testing#instrumented-tests
 * */
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}