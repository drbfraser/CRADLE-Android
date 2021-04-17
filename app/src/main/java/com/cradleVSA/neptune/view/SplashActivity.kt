package com.cradleVSA.neptune.view

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.manager.UpdateManager
import com.cradleVSA.neptune.manager.VersionManager
import com.cradleVSA.neptune.utilitiles.SharedPreferencesMigration
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
    lateinit var versionManager: VersionManager

    @Inject
    lateinit var updateManager: UpdateManager

    val DEFAULT_VERSION_MAJOR: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val isMigrationSuccessful = if (loginManager.isLoggedIn()) {
            SharedPreferencesMigration(sharedPreferences).migrate()
        } else {
            true
        }
        updateManager.immediateUpdate(this@SplashActivity)
        lifecycleScope.launch {
            if (!isMigrationSuccessful) {
                loginManager.logout()
            }

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateManager.handleResult(requestCode, resultCode)
    }

    companion object {
        private const val DELAY_MILLIS = 100L
    }


}
