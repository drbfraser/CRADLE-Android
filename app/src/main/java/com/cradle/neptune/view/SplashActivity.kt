package com.cradle.neptune.view

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import com.cradle.neptune.R
import com.cradle.neptune.manager.LoginManager
import com.cradle.neptune.utilitiles.SharedPreferencesMigration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var loginManager: LoginManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        val isMigrationSuccessful = if (loginManager.isLoggedIn()) {
            SharedPreferencesMigration(sharedPreferences).migrate()
        } else {
            true
        }

        if (!isMigrationSuccessful) {
            runBlocking {
                loginManager.logout()
            }
        }

        Handler().postDelayed(DELAY_MILLIS) {
            val intent = if (isMigrationSuccessful) {
                LoginActivity.makeIntent(this, shouldDisplayMessage = false)
            } else {
                LoginActivity.makeIntent(
                    this,
                    shouldDisplayMessage = true,
                    displayMessageTitleRes = R.string.logout_due_to_error_dialog_title,
                    displayMessageBodyRes = R.string.logout_due_to_error_dialog_message
                )
            }
            startActivity(intent)
            finish()
        }
    }

    companion object {
        private const val DELAY_MILLIS = 100L
    }
}
