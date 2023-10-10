package com.cradleplatform.neptune

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.Observer
import androidx.work.Configuration
import com.cradleplatform.neptune.manager.AssessmentManager
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReadingManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.view.PinPassActivity

import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
    var appKilledLockout = false
    //Mutex for Pin Activity starting
    val lock = Mutex()

    /**
     * Set desired timeout time in milliseconds
     * 30000 = 30 Seconds (For testing)
     * 1800000 = 30 Minutes
     * 86400000 = 24 Hours (For Prod)
     * If this is changed also change change_pin_message in string.xml
     * to match the time
     */
    private val timeoutTime = 1800000

    //SharedPref Variables
    private val timeSharePrefKey = "TIMESTAMP_KEY"
    private val lockOutPrefKey = "LOCKOUT_KEY"
    private val applicationSharedPrefName = "APPLICATION_SHARED_PREF"
    private lateinit var sharedPref: SharedPreferences

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var loginManager: LoginManager
    @Inject
    lateinit var patientManager: PatientManager
    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var referralManager: ReferralManager
    @Inject
    lateinit var assessmentManager: AssessmentManager

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

        //Initialize Sharedpref
        sharedPref = getSharedPreferences(applicationSharedPrefName, Context.MODE_PRIVATE) ?: return
        appKilledLockout = sharedPref.getBoolean(lockOutPrefKey, false)

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
                     *Regarding launchPinActivity
                     *Thread is required since there is a race condition with internal mutexes
                     *This method will make sure pin is on top always and prevent crashes
                     *As well as making sure internal stack order is correct since we use
                     *FLAG_ACTIVITY_NEW_TASK which might mess things up if we try to race
                     *It is hacky, but the only way to launch multiple activities at once
                     *without having all the intents and no coupling
                     *
                     * Mutex is to prevent the opening of multiple instances of the
                     * PIN pass activity
                     **/

                    //If the the app was killed during Pin Activity
                    if (appKilledLockout && lastTimeActive < 1) {
                        if (lock.tryLock())
                            launchPinActvity(activity)
                        appKilledLockout = false
                    }
                    //For auto login if user has not signed on in timeout time and app was closed
                    if (lastTimeActive < 1 && loginManager.isLoggedIn()) {
                        lastTimeActive = sharedPref.getLong(timeSharePrefKey, 0)
                        if (System.currentTimeMillis() > lastTimeActive + timeoutTime) {
                            if (lock.tryLock())
                                launchPinActvity(activity)
                        }
                    }

                    //For if the was just put into background
                    if (System.currentTimeMillis() > lastTimeActive + timeoutTime
                        && lastTimeActive > 0 && loginManager.isLoggedIn()
                    ) {
                        if (lock.tryLock())
                            launchPinActvity(activity)
                    }
                    lastTimeActive = sharedPref.getLong(timeSharePrefKey, 0)
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    //Will track anytime it is not in foreground
                    lastTimeActive = System.currentTimeMillis()

                    with(sharedPref.edit()) {
                        putLong(timeSharePrefKey, lastTimeActive)
                        apply()
                    }
                }
                override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    //If they try to override PIN by killing the app this will still go off first
                    if (pinActivityActive) {
                        appKilledLockout = true
                        with(sharedPref.edit()) {
                            putBoolean(lockOutPrefKey, appKilledLockout)
                            apply()
                        }
                    } else {
                        //This is to check the PIN has been successful and we don't need to
                        //Pin log in when the session is over
                        if (appKilledLockout) {
                            appKilledLockout = false
                            with(sharedPref.edit()) {
                                putBoolean(lockOutPrefKey, appKilledLockout)
                                apply()
                            }
                        }
                    }
                }
            }
        )

        doNetworkCheck()
    }

    private fun doNetworkCheck() {

        val isNetworkAvailable = NetworkAvailableLiveData(this)

        // Note: Observing must be done in View not ViewModel.
        // Throughout application, notify user when internet connection is restored and there is content to sync.
        val networkConnectionObserver = Observer<Boolean> { t ->
            when (t) {
                //    TODO: Check if there is content to sync
                //    First, check if there is local content to upload.
                true -> {
                    Log.d(
                        "CRADLEAPPTAG",
                        "DEBUG: YES INTERNET"
                    )

                    appCoroutineScope.launch {
                        val a = patientManager.getNumberOfPatientsToUpload()
                        val b = readingManager.getNumberOfReadingsToUpload()
                        val c = referralManager.getNumberOfReferralsToUpload()
                        val d = assessmentManager.getNumberOfAssessmentsToUpload()

                        Log.d("CRADLEAPPTAG", "$a $b $c $d")
                        val numberOfItemsToUpload = a + b + c + d
                        if (numberOfItemsToUpload > 0) {
                            Toast.makeText(
                                applicationContext,
                                getString(R.string.network_detected_sync_reminder),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        // Use .observeForever() so that observer lasts even when Activity ends.
        // TODO: only attach once
        isNetworkAvailable.observeForever(networkConnectionObserver)
    }

    fun pinPassActivityFinished() {
        pinActivityActive = false
    }

    fun pinPassActivityStarted() {
        pinActivityActive = true
        lock.unlock()
    }

    fun logOutofSession() {
        appCoroutineScope.launch(Dispatchers.Main) {
            loginManager.logout()
        }
    }

    private fun launchPinActvity(activity: Activity) {
        appCoroutineScope.launch(Dispatchers.Main) {
            //Do not lower delay there are a lot of activities on launch this needs to be last
            delay(1000L)
            val intent = Intent(activity, PinPassActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(FLAG_ACTIVITY_NO_ANIMATION)
            //Flags to make sure this activity is always on top
            intent.addFlags(FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra("isChangePin", false)
            startActivity(intent)
        }
    }
}
