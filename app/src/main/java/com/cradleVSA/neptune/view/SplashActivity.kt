package com.cradleVSA.neptune.view

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
            //getVersionInfo(this@SplashActivity)


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

    suspend fun getVersionInfo(activity: SplashActivity) {
        val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE) ?: return
        val currentVersion = sharedPref.getInt(getString(R.string.key_version_major), DEFAULT_VERSION_MAJOR)
        Log.d("************API Current", currentVersion.toString())
        val version = versionManager.getAPIVersion()

        val versionMajor = version.unwrapped?.version.toString().split(".")[0].toInt()

        if (versionMajor > currentVersion) {
            forceUpdate()
        }

        Log.d("************API VERSION", versionMajor.toString())
        with (sharedPref.edit()) {
            putInt(getString(R.string.key_version_major), versionMajor)
            apply()
        }

        Log.d("************API VERSION", version.toString())
        Log.d("************API VERSION", version.unwrapped?.version.toString())
    }

    private fun forceUpdate() {
        TODO("Not yet implemented")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateManager.handleResult(requestCode, resultCode)

    }

    companion object {
        private const val DELAY_MILLIS = 100L
    }


}
