package com.cradleplatform.neptune.testutils.rules

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class GrantRuntimePermissionsRule: TestRule {
    companion object {
        const val TAG =  "GrantPermissionsRule"
        val PERMISSIONS = arrayOf(
            "CAMERA",
            "INTERNET",
            "ACCESS_NETWORK_STATE",
            "SEND_SMS",
            "RECEIVE_SMS",
            "READ_SMS",
            "SEND_SMS",
            "READ_PHONE_STATE",
            "READ_PHONE_NUMBERS"
        )
    }

    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
                    val packageName = getApplicationContext<Context>().packageName
                    PERMISSIONS.forEach {
                        uiAutomation.executeShellCommand(
                            "pm grant $packageName android.permission.$it"
                        )
                    }
                }
                try {
                    base?.evaluate()
                } finally {
                    Log.d(WorkManagerRule.TAG, "Teardown")
                }
            }
        }
    }
}