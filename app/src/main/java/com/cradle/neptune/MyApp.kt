package com.cradle.neptune

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.jakewharton.threetenabp.AndroidThreeTen
import com.wonderkiln.blurkit.BlurKit
import dagger.hilt.android.HiltAndroidApp

/**
 * Allow access to Dagger single instance of Component
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Suppress("EmptyFunctionBlock")
@HiltAndroidApp
class MyApp : MultiDexApplication() {
    var isDisableBlurKit = false

    override fun onCreate() {
        super.onCreate()
        // Initialize the time library:
        // https://github.com/JakeWharton/ThreeTenABP
        AndroidThreeTen.init(this)

        // Disable rotation
        // source: https://stackoverflow.com/questions/6745797/how-to-set-entire-application-in-portrait-mode-only/9784269#9784269
        // register to be informed of activities starting up
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {

                // new activity created; force its orientation to portrait
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle?) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Blur-Effect
        // encountered bug in the field: init() throwing a android.support.v8.renderscript.RSRuntimeException
        // for at least one user.
        try {
            BlurKit.init(this)
        } catch (e: Exception) {
            Log.e("MyApp", "Failed initializing BlurKit.", e)
            isDisableBlurKit = true
        }
    }
}
