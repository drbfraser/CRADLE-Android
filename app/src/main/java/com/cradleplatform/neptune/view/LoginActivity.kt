package com.cradleplatform.neptune.view

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.view.ui.settings.SettingsActivity.Companion.ADVANCED_SETTINGS_KEY
import com.cradleplatform.neptune.view.ui.settings.SettingsActivity.Companion.makeSettingsActivityLaunchIntent
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var loginManager: LoginManager

    private lateinit var isNetworkAvailable: NetworkAvailableLiveData

    private var idlingResource: CountingIdlingResource? = null

    /**
     * Called only from tests. Creates and returns a new [CountingIdlingResource].
     * This is null if and only if this [ReadingActivity] is not being tested under Espresso.
     */
    @VisibleForTesting
    fun getIdlingResource(): IdlingResource {
        return idlingResource
            ?: CountingIdlingResource("LoginActivity").also {
                idlingResource = it
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showMessageIfPresent()

        if (loginManager.isLoggedIn()) {
            // no need to load anything if already logged in.
            startIntroActivity()
        }

        setContentView(R.layout.activity_login)
        setupLogin()
        setupSettings()
    }

    private fun showMessageIfPresent() {
        if (!intent.getBooleanExtra(EXTRA_SHOULD_DISPLAY_MESSAGE, false)) {
            return
        }

        MaterialAlertDialogBuilder(this).apply {
            intent.getIntExtra(EXTRA_DISPLAY_MESSAGE_TITLE, 0).let { resId ->
                if (resId != 0) {
                    setTitle(resId)
                }
            }
            intent.getIntExtra(EXTRA_DISPLAY_MESSAGE_BODY, 0).let { resId ->
                if (resId != 0) {
                    setMessage(resId)
                }
            }
            setPositiveButton(android.R.string.ok, null)
        }.show()
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
        val noInternetText = findViewById<TextView>(R.id.internetAvailabilityTextView)
        val loginButton = findViewById<Button>(R.id.loginButton)

        isNetworkAvailable = NetworkAvailableLiveData(this).apply {
            observe(this@LoginActivity) { netAvailable ->
                netAvailable ?: return@observe
                loginButton.isEnabled = netAvailable
                noInternetText.isVisible = !netAvailable
            }
        }

        loginButton.setOnClickListener {
            idlingResource?.increment()

            errorText.visibility = View.GONE
            val progressDialog = progressDialog
            passwordET.hideKeyboard()

            lifecycleScope.launch {
                val email = emailET.text.toString()
                val password = passwordET.text.toString()
                val result = loginManager.login(email, password)
                progressDialog.cancel()

                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.login_successful),
                            Toast.LENGTH_SHORT
                        ).show()
                        startIntroActivity()
                    }
                    is NetworkResult.Failure -> {
                        errorText.apply {
                            visibility = View.VISIBLE
                            text = if (result.statusCode == HTTP_UNAUTHORIZED) {
                                getString(R.string.login_error)
                            } else {
                                getString(
                                    R.string.login_error_with_status_code,
                                    result.statusCode
                                )
                            }
                        }
                    }
                    is NetworkResult.NetworkException -> {
                        errorText.visibility = View.VISIBLE
                        if (result.cause is SSLHandshakeException) {
                            FirebaseCrashlytics.getInstance().recordException(result.cause)
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
                            errorText.text = getString(
                                R.string.login_error_general_error_exception_name,
                                result.cause.javaClass.simpleName
                            )
                        }
                    }
                }

                idlingResource?.decrement()
            }
        }

        setUpTogglePasswordButtonListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpTogglePasswordButtonListener() {
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val imageButton: ImageButton = findViewById<ImageButton>(R.id.togglePasswordButton)
        imageButton.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources, R.drawable.baseline_visibility_24, null
            )
        )

        imageButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    passwordEditText.inputType = InputType.TYPE_CLASS_TEXT
                    imageButton.contentDescription = resources.getString(R.string.hide_password)
                    imageButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.baseline_visibility_off_24, null
                        )
                    )

                    passwordEditText.setSelection(passwordEditText.text.length)
                }

                MotionEvent.ACTION_UP -> {
                    passwordEditText.transformationMethod =
                        HideReturnsTransformationMethod.getInstance()
                    passwordEditText.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    imageButton.contentDescription = resources.getString(R.string.show_password)
                    imageButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.baseline_visibility_24, null
                        )
                    )

                    passwordEditText.setSelection(passwordEditText.text.length)
                }
            }
            true
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
                this@LoginActivity,
                e.connectionStatusCode
            )
        } catch (e: GooglePlayServicesNotAvailableException) {
            MaterialAlertDialogBuilder(this@LoginActivity)
                .setTitle(R.string.login_activity_device_is_outdated_dialog_title)
                .setMessage(R.string.login_activity_device_is_outdated_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setupSettings() {
        val settingsButton = findViewById<ImageButton>(R.id.loginSettingsButton)
        settingsButton.setOnClickListener {
            val intent = makeSettingsActivityLaunchIntent(this@LoginActivity)
            intent.putExtra(ADVANCED_SETTINGS_KEY, true)
            startActivity(intent)
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

    companion object {
        private const val EXTRA_SHOULD_DISPLAY_MESSAGE = "display_message_bool"
        private const val EXTRA_DISPLAY_MESSAGE_TITLE = "display_message_title"
        private const val EXTRA_DISPLAY_MESSAGE_BODY = "display_message_body"

        fun makeIntent(
            context: Context,
            shouldDisplayMessage: Boolean,
            @StringRes displayMessageTitleRes: Int? = null,
            @StringRes displayMessageBodyRes: Int? = null
        ) = Intent(context, LoginActivity::class.java).apply {
            putExtra(EXTRA_SHOULD_DISPLAY_MESSAGE, shouldDisplayMessage)
            displayMessageTitleRes?.let { putExtra(EXTRA_DISPLAY_MESSAGE_TITLE, it) }
            displayMessageBodyRes?.let { putExtra(EXTRA_DISPLAY_MESSAGE_BODY, it) }
        }
    }
}
