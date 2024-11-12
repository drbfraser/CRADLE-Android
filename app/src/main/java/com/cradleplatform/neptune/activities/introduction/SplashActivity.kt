package com.cradleplatform.neptune.activities.introduction

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.UpdateManager
import com.cradleplatform.neptune.utilities.SharedPreferencesMigration
import com.cradleplatform.neptune.utilities.notification.NotificationManagerCustom
import com.cradleplatform.neptune.activities.authentication.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var loginManager: LoginManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // As recommended by Google. Since we want our app to push notifications,
        // This should the first thing to
        createNotificationChannel()

        val isMigrationSuccessful = if (loginManager.isLoggedIn()) {
            SharedPreferencesMigration(sharedPreferences).migrate()
        } else {
            true
        }

        lifecycleScope.launch {
            if (!isMigrationSuccessful) {
                loginManager.logout()
            }
            updateManager.immediateUpdate(this@SplashActivity)
            delay(DELAY_MILLIS)

            val intent = if (isMigrationSuccessful) {
                LoginActivity.makeIntent(this@SplashActivity, shouldDisplayMessage = false)
            } else {
                LoginActivity.makeIntent(
                    this@SplashActivity,
                    shouldDisplayMessage = true,
                    displayMessageTitleRes = R.string.logout_due_to_error_dialog_title,
                    displayMessageBodyRes = R.string.logout_due_to_error_dialog_message
                )
            }

            startActivity(intent)
            finish()
        }
    }

    private fun createNotificationChannel() {
        NotificationManagerCustom.createNotificationChannel(this)
    }

    companion object {
        private const val DELAY_MILLIS = 100L
    }
}
