package com.cradleplatform.neptune

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cradleplatform.neptune.view.PinPassActivity

import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Allow access to Dagger single instance of Component
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Suppress("EmptyFunctionBlock")
@HiltAndroidApp
class CradleApplication : Application(), Configuration.Provider {
    var isDisableBlurKit = false
    var lastTimeActive: Long = 0
    var pinActivityActive: Boolean = false

    //Set desired timeout time in milliseconds
    val timeoutTime = 30000

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

    /**
     * Meant for coroutines that should outlive the lifetime of Fragments, Activities, etc.
     */
    val appCoroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize the time library:
        // https://github.com/JakeWharton/ThreeTenABP
        AndroidThreeTen.init(this)

        // Disable rotation
        // source: https://stackoverflow.com/questions/6745797/how-to-set-entire-application-in-portrait-mode-only/9784269#9784269
        // register to be informed of activities starting up
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                    // new activity created; force its orientation to portrait
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

                    /**
                     *Thread is required since there is a race condition with internal mutexes
                     *This method will make sure pin is on top always and prevent crashes
                     *As well as making sure internal stack order is correct since we use
                     *FLAG_ACTIVITY_NEW_TASK which might mess things up if we try to race
                     *It is hacky, but the only way to launch multiple activities at once
                     *without having all the intents
                     *
                     * @pinActivityActive is required
                     * since onActivityCreated pops multiple times sometimes
                     **/

                    if (System.currentTimeMillis() > lastTimeActive + timeoutTime
                        && lastTimeActive > 0 && !pinActivityActive
                    ) {
                        Thread {
                            Thread.sleep(500)
                            pinActivityActive = true
                            val intent = Intent(activity, PinPassActivity::class.java)
                            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }.start()
                    }
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    //Will track anytime it is not in foreground
                    lastTimeActive = System.currentTimeMillis()
                }
                override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    public fun pinPassActivityFinished() {
        pinActivityActive = false
    }
}
