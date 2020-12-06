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
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.cradleVSA.neptune.R
import com.cradleVSA.neptune.cryptography.EncryptedSharedPreferences
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
import com.google.crypto.tink.Aead
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AesGcmKeyManager
import com.google.crypto.tink.daead.AesSivKeyManager
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.signal.argon2.Argon2
import org.signal.argon2.MemoryCost
import org.signal.argon2.Type
import org.signal.argon2.Version
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.security.GeneralSecurityException
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

    private data class Argon2HashConfig(
        val memory: MemoryCost,
        val hashParallelism: Int,
        val hashIterations: Int,
        val hashSizeBytes: Int,
        val salt: ByteArray,
        val version: Version = Version.V13,
        val type: Type = Type.Argon2id
    ) {
        fun toJson() = JSONObject().apply {
            put("memory_KiB", memory.kiB)
            put("parallelism", hashParallelism)
            put("iterations", hashIterations)
            put("size_bytes", hashSizeBytes)
            put("salt", Base64.encodeToString(salt, /* flags= */ 0))
            put("version", version.toString())
            put("type", type.toString())
        }

        fun createHash(input: ByteArray): ByteArray = Argon2.Builder(version)
            .type(type)
            .memoryCost(memory)
            .parallelism(hashParallelism)
            .iterations(hashIterations)
            .hashLength(hashSizeBytes)
            .build()
            .hash(input, salt)
            .hash

        companion object {
            fun fromJson(jsonObject: JSONObject) = Argon2HashConfig(
                memory = MemoryCost.KiB(jsonObject.getInt("memory_KiB")),
                hashParallelism = jsonObject.getInt("parallelism"),
                hashIterations = jsonObject.getInt("iterations"),
                hashSizeBytes = jsonObject.getInt("size_bytes"),
                salt = Base64.decode(jsonObject.getString("salt"), /* flags= */ 0),
                version = Version.valueOf(jsonObject.getString("version")),
                type = Type.valueOf(jsonObject.getString("type"))
            )
        }
    }

    private fun setupLogin() {
        val emailET = findViewById<EditText>(R.id.emailEditText)
        val passwordET = findViewById<EditText>(R.id.passwordEditText)
        passwordET.transformationMethod = PasswordTransformationMethod()
        val errorText = findViewById<TextView>(R.id.invalidLoginText)
        val loginButton = findViewById<Button>(R.id.loginButton)

        // https://www.usenix.org/sites/default/files/conference/protected-files/hotsec15_slides_green.pdf
        // https://github.com/google/tink/issues/347

        // Do this in the Application class
        AeadConfig.register()
        DeterministicAeadConfig.register()

        // for printing purposes
        val context = this as Context

        val keyDirectory = context.getDir("keys", MODE_PRIVATE)
        val hashParamsFile = File(keyDirectory, "password-argon2-params")
        if (!hashParamsFile.exists()) {
            @Suppress("MagicNumber")
            // TODO: Change these parameters. Hash size must stay at 32 to use 256-bit keys
            val params = Argon2HashConfig(
                memory = MemoryCost.MiB(16),
                hashParallelism = 1,
                hashIterations = 32,
                hashSizeBytes = 32,
                salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            )
            FileOutputStream(hashParamsFile).use {
                it.write(params.toJson().toString().encodeToByteArray())
            }
        }
        val hashParamsJson = FileInputStream(hashParamsFile).use {
            JSONObject(it.readBytes().decodeToString())
        }
        val hashConfig = Argon2HashConfig.fromJson(hashParamsJson)
        val userPassword = "somePassword123"
        val passwordHash = hashConfig.createHash(userPassword.toByteArray(Charsets.UTF_8))
        val passwordKey = AesGcmJce(passwordHash)

        val mainKeyFile = File(keyDirectory, "main-key-password-encrypted")
        if (!mainKeyFile.exists()) {
            val aesGcmKeysetHandle = KeysetHandle.generateNew(AesGcmKeyManager.aes256GcmTemplate())
            aesGcmKeysetHandle.write(JsonKeysetWriter.withFile(mainKeyFile), passwordKey)
        }

        try {
            val mainKeyset = KeysetHandle.read(JsonKeysetReader.withFile(mainKeyFile), passwordKey)
            val aeadFromMainKeyset = mainKeyset.getPrimitive(Aead::class.java)

            val message = "This is a message"

            @Suppress("MagicNumber")
            val aad = ByteArray(20).apply { SecureRandom().nextBytes(this) }
            val cipherText = aeadFromMainKeyset.encrypt(message.toByteArray(Charsets.UTF_8), aad)
            Log.d(
                "LoginActivity",
                "ciphertext is ${Base64.encodeToString(cipherText, Base64.DEFAULT)}"
            )
            val decrypt = aeadFromMainKeyset.decrypt(cipherText, aad)
            Log.d("LoginActivity", "decrypted ciphertext is is ${decrypt.decodeToString()}")

            val prefValueEncryptionKeyFile = File(keyDirectory, "pref-values-encryption-key")
            if (!prefValueEncryptionKeyFile.exists()) {
                val aesGcmKeysetHandle =
                    KeysetHandle.generateNew(AesGcmKeyManager.aes256GcmTemplate())
                aesGcmKeysetHandle.write(
                    JsonKeysetWriter.withFile(prefValueEncryptionKeyFile),
                    aeadFromMainKeyset
                )
            }
            val prefKeysEncryptionKeyFile = File(keyDirectory, "pref-keys-encryption-key")
            if (!prefKeysEncryptionKeyFile.exists()) {
                val aesSivKeysetHandle =
                    KeysetHandle.generateNew(AesSivKeyManager.aes256SivTemplate())
                aesSivKeysetHandle.write(
                    JsonKeysetWriter.withFile(prefKeysEncryptionKeyFile),
                    aeadFromMainKeyset
                )
            }

            val keysetForSharedPrefKeys = KeysetHandle.read(
                JsonKeysetReader.withFile(prefKeysEncryptionKeyFile),
                aeadFromMainKeyset
            )
            val keysetForSharedPrefValues = KeysetHandle.read(
                JsonKeysetReader.withFile(prefValueEncryptionKeyFile),
                aeadFromMainKeyset
            )

            val encryptedSharedPreferences = EncryptedSharedPreferences.create(
                "shared-pref-encrypted",
                this,
                keysetForSharedPrefKeys,
                keysetForSharedPrefValues
            )

            val existingTestInt = encryptedSharedPreferences.getLong("test_long_key", -1L)
            Log.d("LoginActivity", "DEBUG: test_long_key from before is $existingTestInt")
            encryptedSharedPreferences.edit(commit = true) {
                @Suppress("MagicNumber")
                putLong("test_long_key", System.currentTimeMillis() / 1000)
            }
        } catch (e: IOException) {
            Log.e("LoginActivity", "got IOException during encryption", e)
        } catch (e: GeneralSecurityException) {
            Log.e("LoginActivity", "got GeneralSecurityException during encryption", e)
        }

        /*
         * result looks like this
         *
         * <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
         * <map>
         *     <string name="AQtPf2KGy5/fWchOi9oZSom1TRK4pHvw">
                    AQLrmdBfOooRE9B9ZP/0waXbhQ+Vj6aNRKJdN61AoB5p2RaImh5BqhU=</string>
         * </map>
         *
         */

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
