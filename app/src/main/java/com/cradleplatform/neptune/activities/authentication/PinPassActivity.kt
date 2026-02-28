package com.cradleplatform.neptune.activities.authentication

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.cradleplatform.neptune.CradleApplication
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.dashboard.DashBoardActivity
import com.cradleplatform.neptune.viewmodel.PinPassViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PinPassActivity : AppCompatActivity() {
    lateinit var app: CradleApplication

    private lateinit var pinCodePrefKey: String

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val extraChangePin = "isChangePin"

    lateinit var pinCode: String

    private lateinit var defaultPinCode: String

    private val viewModel: PinPassViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_pass)
        pinCodePrefKey = getString(R.string.key_pin_shared_key)

        //Default Pin is aaaa
        defaultPinCode = getString(R.string.key_pin_default_pin)
        val isChangePinActive = intent.getBooleanExtra(extraChangePin, false)
        pinCode = sharedPreferences.getString(pinCodePrefKey, defaultPinCode)!!

        if (!isChangePinActive) {
            app = this.application as CradleApplication
            if (pinCode == defaultPinCode) {
                Toast.makeText(this, getString(R.string.pin_not_set), Toast.LENGTH_LONG).show()
                app.pinPassActivityFinished()
                app.logOutofSession()
                val intent = Intent(this@PinPassActivity, LoginActivity::class.java)
                startActivity(intent)
                finish()
                return
            }

            // Only unlock mutex if it's actually locked (i.e., when coming from background, not initial launch)
            if (app.lock.isLocked) {
                app.pinPassActivityStarted()
            } else {
                // Just mark as active without unlocking
                app.pinActivityActive = true
            }
            setUpButtons(isChangePinActive)
            setUpPIN(isChangePinActive)
        } else {
            setUpPIN(isChangePinActive)
            setUpButtons(isChangePinActive)
        }
    }

    private fun setUpButtons(isChangePinActive: Boolean) {
        val forgotButton = findViewById<Button>(R.id.pinPassForgotButton)

        if (isChangePinActive) {
            forgotButton.text = getString(R.string.cancel)
            forgotButton.setOnClickListener {
                finish()
            }
        } else {
            forgotButton.setOnClickListener {
                AlertDialog.Builder(this@PinPassActivity)
                    .setMessage(R.string.pin_alert_message)
                    .setCancelable(true)
                    .setTitle(R.string.confirm)
                    .setPositiveButton(R.string.sign_out_dialog_yes_button) { _, _ ->
                        app.pinPassActivityFinished()
                        app.logOutofSession()
                        val intent = Intent(this@PinPassActivity, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                    }
                    .show()
            }
        }
    }

    private fun setUpPIN(isChangePinActive: Boolean) {
        val headerLine = findViewById<TextView>(R.id.pinPassText)
        val passText = findViewById<EditText>(R.id.pinPassEdit)
        val incorrectText = findViewById<TextView>(R.id.pinPassIncorrectText)

        if (isChangePinActive) {
            // Restore state from ViewModel (survives rotation)
            headerLine.text = if (viewModel.headerText.isNotEmpty()) {
                viewModel.headerText
            } else {
                getString(R.string.change_pin_act_first_enter).also {
                    viewModel.headerText = it
                }
            }

            passText.doAfterTextChanged {
                if (!viewModel.isConfirmingPin && passText.text.toString().length == 4) {
                    viewModel.tempPin = passText.text.toString()
                    viewModel.isConfirmingPin = true
                    val confirmText = getString(R.string.change_pin_act_second_enter)
                    viewModel.headerText = confirmText
                    headerLine.text = confirmText
                    passText.setText("")
                } else if (viewModel.isConfirmingPin && passText.text.toString().length == 4) {
                    if (passText.text.toString() == viewModel.tempPin) {
                        with(sharedPreferences.edit()) {
                            putString(pinCodePrefKey, viewModel.tempPin)
                            apply()
                        }
                        Toast.makeText(this, getString(R.string.change_pin_act_pin_saved), Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        incorrectText.text = getString(R.string.change_pin_act_incorrect)
                        passText.setText("")
                    }
                }
            }
        } else {
            passText.doAfterTextChanged {
                if (passText.text.toString() == pinCode) {
                    app.pinPassActivityFinished()
                    val intent = Intent(this, DashBoardActivity::class.java)
                    startActivity(intent)
                    finish()
                } else if (passText.text.toString().length == 4) {
                    incorrectText.text = getString(R.string.pinpass_incorrect_password)
                    passText.setText("")
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val isChangePinActive = intent.getBooleanExtra(extraChangePin, false)
        if (isChangePinActive) {
            // Allow back navigation when changing PIN
            super.onBackPressed()
        }
        // Do not allow user to leave this screen until password is entered when verifying PIN
    }
}
