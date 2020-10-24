package com.cradle.neptune.dagger

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.jakewharton.threetenabp.AndroidThreeTen
import com.wonderkiln.blurkit.BlurKit
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * Allow access to Dagger single instance of Component
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
@Suppress("EmptyFunctionBlock")
class MyApp : MultiDexApplication(), HasAndroidInjector {
    @Inject
    lateinit var activityInjector: DispatchingAndroidInjector<Activity>

    val appComponent: AppComponent by lazy {
        DaggerAppComponent.builder()
            .appModule(AppModule(this))
            .dataModule(DataModule())
            .viewModelModule(ViewModelModule())
            .build()
    }
    var isDisableBlurKit = false

    override fun onCreate() {
        super.onCreate()
        appComponent.inject(this)
        // Initialize the time library:
        // https://github.com/JakeWharton/ThreeTenABP
        AndroidThreeTen.init(this)

        // If a Dagger 2 component does not have any constructor arguments for any of its modules,
        // then we can use .create() as a shortcut instead:
        //  mAppComponent = com.codepath.dagger.components.DaggerAppComponent.create();

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

    @Suppress("UNCHECKED_CAST")
    override fun androidInjector(): AndroidInjector<Any> = activityInjector as AndroidInjector<Any>
}
