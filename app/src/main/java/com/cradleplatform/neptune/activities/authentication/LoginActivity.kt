package com.cradleplatform.neptune.activities.authentication

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LiveData
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityLoginBinding
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.activities.introduction.IntroActivity
import com.cradleplatform.neptune.activities.settings.SettingsActivity.Companion.ADVANCED_SETTINGS_KEY
import com.cradleplatform.neptune.activities.settings.SettingsActivity.Companion.makeSettingsActivityLaunchIntent
import com.cradleplatform.neptune.viewmodel.LoginState
import com.cradleplatform.neptune.viewmodel.LoginViewModel
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var networkStateManager: NetworkStateManager

    private lateinit var isNetworkAvailable: LiveData<Boolean>
    private var idlingResource: CountingIdlingResource? = null

    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog? = null

    /**
     * Called only from tests. Creates and returns a new [CountingIdlingResource].
     * This is null if and only if this [LoginActivity] is not being tested under Espresso.
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
        if (viewModel.isLoggedIn()) {
            // no need to load anything if already logged in.
            startIntroActivity()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupLogin()
        setupSettings()
        setupNetworkStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun setupObservers() {
        viewModel.loginState.observe(this) { state ->
            handleLoginState(state)
        }
    }

    private fun handleLoginState(state: LoginState) {
        when (state) {
            is LoginState.Idle -> {
                progressDialog?.dismiss()
                progressDialog = null
                binding.invalidLoginText.visibility = View.GONE
            }
            is LoginState.Loading -> {
                binding.invalidLoginText.visibility = View.GONE
                progressDialog = createProgressDialog()
            }
            is LoginState.Success -> {
                progressDialog?.dismiss()
                progressDialog = null
                idlingResource?.decrement()
                Toast.makeText(
                    this,
                    getString(R.string.login_successful),
                    Toast.LENGTH_SHORT
                ).show()
                startIntroActivity()
            }
            is LoginState.Error -> {
                progressDialog?.dismiss()
                progressDialog = null
                idlingResource?.decrement()
                binding.invalidLoginText.apply {
                    visibility = View.VISIBLE
                    text = if (state.statusCode == HTTP_UNAUTHORIZED) {
                        getString(R.string.login_error)
                    } else {
                        getString(
                            R.string.login_error_with_status_code,
                            state.statusCode
                        )
                    }
                }
            }
            is LoginState.NetworkError -> {
                progressDialog?.dismiss()
                progressDialog = null
                idlingResource?.decrement()
                binding.invalidLoginText.visibility = View.VISIBLE
                if (state.exception is SSLHandshakeException) {
                    FirebaseCrashlytics.getInstance().recordException(state.exception)
                    binding.invalidLoginText.text = getString(R.string.login_error_ssl_handshake_exception)
                    Log.d("LoginActivity", "attempting to run ProviderInstaller")
                    Toast.makeText(
                        this,
                        R.string.login_activity_security_provider_toast,
                        Toast.LENGTH_LONG
                    ).show()
                    // Attempt to update user's TLS version if they're outdated
                    attemptProviderInstallerUpdate()
                } else {
                    binding.invalidLoginText.text = getString(
                        R.string.login_error_general_error_exception_name,
                        state.exception.javaClass.simpleName
                    )
                }
            }
        }
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
        // Restore email and password if available (e.g., from config changes)
        viewModel.email.value?.let { binding.emailEditText.setText(it) }
        viewModel.password.value?.let { binding.passwordEditText.setText(it) }

        // Save email and password to ViewModel as user types
        binding.emailEditText.doAfterTextChanged {
            viewModel.setEmail(it?.toString() ?: "")
        }

        binding.passwordEditText.doAfterTextChanged {
            viewModel.setPassword(it?.toString() ?: "")
        }

        binding.loginButton.setOnClickListener {
            idlingResource?.increment()
            binding.passwordEditText.hideKeyboard()

            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            viewModel.login(email, password)
        }

        setUpTogglePasswordButtonListener()
    }

    private fun setupNetworkStatus() {
        isNetworkAvailable = networkStateManager.getInternetConnectivityStatus().apply {
            observe(this@LoginActivity) { netAvailable ->
                netAvailable ?: return@observe
                binding.loginButton.isEnabled = netAvailable
                binding.internetAvailabilityTextView.isVisible = !netAvailable
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpTogglePasswordButtonListener() {
        binding.togglePasswordButton.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources, R.drawable.baseline_visibility_24, null
            )
        )

        binding.togglePasswordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.passwordEditText.inputType = InputType.TYPE_CLASS_TEXT
                    binding.togglePasswordButton.contentDescription = resources.getString(R.string.hide_password)
                    binding.togglePasswordButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.baseline_visibility_off_24, null
                        )
                    )

                    binding.passwordEditText.setSelection(binding.passwordEditText.text?.length ?: 0)
                }

                MotionEvent.ACTION_UP -> {
                    binding.passwordEditText.transformationMethod =
                        HideReturnsTransformationMethod.getInstance()
                    binding.passwordEditText.inputType =
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    binding.togglePasswordButton.contentDescription = resources.getString(R.string.show_password)
                    binding.togglePasswordButton.setImageDrawable(
                        ResourcesCompat.getDrawable(
                            resources, R.drawable.baseline_visibility_24, null
                        )
                    )

                    binding.passwordEditText.setSelection(binding.passwordEditText.text?.length ?: 0)
                }
            }
            true
        }
    }

    /**
     * Tries to upgrade the device's security provider in case their TLS version is out of date.
     * https://medium.com/tech-quizlet/working-with-tls-1-2-on-android-4-4-and-lower-f4f5205629a
     */
    private fun attemptProviderInstallerUpdate() {
        try {
            ProviderInstaller.installIfNeeded(this@LoginActivity)
        } catch (e: GooglePlayServicesRepairableException) {
            GoogleApiAvailability.getInstance().showErrorNotification(
                this@LoginActivity,
                e.connectionStatusCode
            )
        } catch (@Suppress("SwallowedException") e: GooglePlayServicesNotAvailableException) {
            MaterialAlertDialogBuilder(this@LoginActivity)
                .setTitle(R.string.login_activity_device_is_outdated_dialog_title)
                .setMessage(R.string.login_activity_device_is_outdated_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun setupSettings() {
        binding.loginSettingsButton.setOnClickListener {
            val intent = makeSettingsActivityLaunchIntent(this@LoginActivity)
            intent.putExtra(ADVANCED_SETTINGS_KEY, true)
            startActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun createProgressDialog(): ProgressDialog {
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

    @Deprecated("Deprecated in Java")
    @Suppress("OVERRIDE_DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        //Do not allow user to leave this screen until password is entered or app exited
        //This is due to if opened from @PinPassActivity pressing back allow user to get back to app
        // Intentionally not calling super.onBackPressed() to prevent navigation
    }
}
