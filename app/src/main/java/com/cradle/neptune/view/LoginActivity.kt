package com.cradle.neptune.view

import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.ext.hideKeyboard
import com.cradle.neptune.manager.LoginManager
import com.cradle.neptune.net.Failure
import com.cradle.neptune.net.NetworkException
import com.cradle.neptune.net.Success
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var loginManager: LoginManager

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MyApp).appComponent.inject(this)
        // no need to load anything if already logged in.
        checkSharedPrefForLogin()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupLogin()
    }

    private fun checkSharedPrefForLogin() {
        val email = sharedPreferences.getString(LoginManager.EMAIL_KEY, null)
        if (email != null) {
            startIntroActivity()
        }
    }

    private fun startIntroActivity() {
        val intent = Intent(this@LoginActivity, IntroActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
        finish()
    }

    private fun setupLogin() {
        val emailET = findViewById<EditText>(R.id.emailEditText)
        val passwordET = findViewById<EditText>(R.id.passwordEditText)
        val errorText = findViewById<TextView>(R.id.invalidLoginText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        loginButton.setOnClickListener { _ ->
            errorText.visibility = View.GONE
            val progressDialog = progressDialog
            passwordET.hideKeyboard()

            MainScope().launch {
                val email = emailET.text.toString()
                val password = passwordET.text.toString()
                val result = loginManager.login(email, password)
                progressDialog.cancel()

                when (result) {
                    is Success -> {
                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.login_successful),
                            Toast.LENGTH_SHORT
                        ).show()
                        startIntroActivity()
                    }
                    is Failure -> {
                        errorText.visibility = View.VISIBLE
                        errorText.text = getString(R.string.login_error)
                    }
                    is NetworkException -> {
                        errorText.visibility = View.VISIBLE
                        if (result.cause is SSLHandshakeException) {
                            errorText.text = getString(R.string.login_error_ssl_handshake_exception)
                            Log.d("LoginActivity", "attempting to run ProviderInstaller")
                            Toast.makeText(
                                this@LoginActivity,
                                R.string.login_activity_security_provider_toast,
                                Toast.LENGTH_LONG
                            ).show()
                            // Attempt to update user's TLS version if they're outdated
                            // source: https://medium.com/tech-quizlet/
                            // working-with-tls-1-2-on-android-4-4-and-lower-f4f5205629a
                            attemptProviderInstallerUpdate()
                        } else {
                            errorText.text = getString(R.string.login_error_general_error)
                        }
                    }
                }
            }
        }
    }

    /**
     * Tries to upgrade the device's security provider in case their TLS version is out of date.
     * https://medium.com/tech-quizlet/working-with-tls-1-2-on-android-4-4-and-lower-f4f5205629a
     */
    private suspend fun attemptProviderInstallerUpdate() = withContext(Dispatchers.Main) {
        try {
            ProviderInstaller.installIfNeeded(this@LoginActivity)
        } catch (e: GooglePlayServicesRepairableException) {
            GoogleApiAvailability.getInstance().showErrorNotification(
                this@LoginActivity, e.connectionStatusCode
            )
        } catch (e: GooglePlayServicesNotAvailableException) {
            MaterialAlertDialogBuilder(this@LoginActivity)
                .setTitle(R.string.login_activity_device_is_outdated_dialog_title)
                .setMessage(R.string.login_activity_device_is_outdated_dialog_message)
                .setPositiveButton(android.R.string.ok) { _, _ -> /* noop */ }
                .show()
        }
    }

    private val progressDialog: ProgressDialog
        get() {
            val progressDialog = ProgressDialog(this@LoginActivity)
            progressDialog.setTitle(getString(R.string.logging_in))
            progressDialog.setCancelable(false)
            progressDialog.show()
            return progressDialog
        }
}
