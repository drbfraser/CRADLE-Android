package com.cradleplatform.neptune.testutils.rules

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * https://stackoverflow.com/questions/63671323/hilt-instrumentation-test-with-workmanager-not-working
 * */
class WorkManagerRule : TestRule {
    companion object {
        const val TAG =  "WorkManagerRule"
    }

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val config = Configuration.Builder()
                    .setMinimumLoggingLevel(Log.DEBUG)
                    .setExecutor(SynchronousExecutor())
//                    .setWorkerFactory()
                    .build()
                if (!WorkManager.isInitialized()) {
                    WorkManager.initialize(context, config)
                }
                try {
                    base?.evaluate()
                } finally {
                    Log.d(TAG, "Teardown")
                }
            }

        }
    }
}