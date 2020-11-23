package com.cradleVSA.neptune.view

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.ext.hideKeyboard
import com.cradleVSA.neptune.manager.LoginManager
import com.cradleVSA.neptune.net.Failure
import com.cradleVSA.neptune.net.NetworkException
import com.cradleVSA.neptune.net.Success
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.argon2.Argon2
import org.signal.argon2.MemoryCost
import org.signal.argon2.Type
import org.signal.argon2.Version
import java.io.File
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.security.SecureRandom
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var loginManager: LoginManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showMessageIfPresent()

        if (loginManager.isLoggedIn()) {
            // no need to load anything if already logged in.
            startIntroActivity()
        }

        setContentView(R.layout.activity_login)
        setupLogin()
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
        passwordET.transformationMethod = PasswordTransformationMethod()
        val errorText = findViewById<TextView>(R.id.invalidLoginText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // https://www.usenix.org/sites/default/files/conference/protected-files/hotsec15_slides_green.pdf
        // https://github.com/google/tink/issues/347
        @Suppress("MagicNumber")
        val argon = Argon2.Builder(Version.V13)
            .type(Type.Argon2id)
            .memoryCost(MemoryCost.MiB(16))
            .parallelism(1)
            .iterations(32)
            .hashLength(32)
            .build()
            .hash(
                "hunter2".toByteArray(Charsets.UTF_8),
                ByteArray(16).apply { SecureRandom().nextBytes(this) }
            )
        val theKey = AesGcmJce(argon.hash)
        Log.d("LoginActivity", "hunter2 encoded with salt: ${argon.encoded}")
        @Suppress("MagicNumber")
        val aad = ByteArray(20).apply { SecureRandom().nextBytes(this) }
        val cipherText = theKey.encrypt(
            "this is a message".toByteArray(Charsets.UTF_8),
            aad
        )
        Log.d("LoginActivity", "ciphertext is ${Base64.encodeToString(cipherText, Base64.DEFAULT)}")
        val decrypt = theKey.decrypt(cipherText, aad)
        Log.d("LoginActivity", "decrypted ciphertext is is ${decrypt.decodeToString()}")

        AeadConfig.register()
        val keysetTemplate = AesGcmKeyManager.aes256GcmTemplate()
        val keysetHandle = KeysetHandle.generateNew(keysetTemplate)
        val dir = getDir("keys", MODE_PRIVATE)
        val file = File(dir, "private-key")
        keysetHandle.write(JsonKeysetWriter.withFile(file), theKey)

        // keysetHandle.getPrimitive(AesGcmJce::class.java)
        @Suppress("MagicNumber")
        val argon2 = Argon2.Builder(Version.V13)
            .type(Type.Argon2id)
            .memoryCost(MemoryCost.MiB(16))
            .parallelism(1)
            .iterations(32)
            .hashLength(32)
            .build()
            .hash(
                "hunter2abc".toByteArray(Charsets.UTF_8),
                ByteArray(16).apply { SecureRandom().nextBytes(this) }
            )
        val theKey2 = AesGcmJce(argon2.hash)
        val file2 = File(dir, "private-key-with-biometrics")
        keysetHandle.write(JsonKeysetWriter.withFile(file2), theKey2)
        keysetHandle.keysetInfo.primaryKeyId

        loginButton.setOnClickListener { _ ->
            errorText.visibility = View.GONE
            val progressDialog = progressDialog
            passwordET.hideKeyboard()

            lifecycleScope.launch {
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
                    is NetworkException -> {
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
