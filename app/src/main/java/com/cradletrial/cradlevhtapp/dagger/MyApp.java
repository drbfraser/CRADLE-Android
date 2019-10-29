package com.cradletrial.cradlevhtapp.dagger;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.wonderkiln.blurkit.BlurKit;

/**
 * Allow access to Dagger single instance of Component
 * Source: https://github.com/codepath/android_guides/wiki/Dependency-Injection-with-Dagger-2#instantiating-the-component
 */
public class MyApp extends Application {
    private static MyApp sInstance;
    private AppComponent mAppComponent;
    private boolean mDisableBlurKit;

    public static MyApp getInstance() {
        return sInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        // Initialize the time library:
        // https://github.com/JakeWharton/ThreeTenABP
        AndroidThreeTen.init(this);


        // Dagger
        mAppComponent = DaggerAppComponent.builder()
                // list of modules that are part of this component need to be created here too
                .appModule(new AppModule(this)) // This also corresponds to the name of your module: %component_name%Module
                .dataModule(new DataModule())
                .build();

        // If a Dagger 2 component does not have any constructor arguments for any of its modules,
        // then we can use .create() as a shortcut instead:
        //  mAppComponent = com.codepath.dagger.components.DaggerAppComponent.create();


        // Disable rotation
        // source: https://stackoverflow.com/questions/6745797/how-to-set-entire-application-in-portrait-mode-only/9784269#9784269
        // register to be informed of activities starting up
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            @Override
            public void onActivityCreated(Activity activity,
                                          Bundle savedInstanceState) {

                // new activity created; force its orientation to portrait
                activity.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });

        // Blur-Effect
        // encountered bug in the field: init() throwing a android.support.v8.renderscript.RSRuntimeException
        // for at least one user.
        try {
            BlurKit.init(this);
        } catch (Exception e) {
            Log.e("MyApp", "Failed initializing BlurKit.", e);
            mDisableBlurKit = true;
        }
    }

    public AppComponent getAppComponent() {
        return mAppComponent;
    }

    public boolean isDisableBlurKit() {
        return mDisableBlurKit;
    }
}
