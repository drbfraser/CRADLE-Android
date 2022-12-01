package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.cradleplatform.neptune.CradleApplication
import com.cradleplatform.neptune.R

class PinPassActivity : AppCompatActivity() {
    lateinit var app: CradleApplication

    private lateinit var pinCodePrefKey: String
    private lateinit var pinPassSharedPreferences: String
    private lateinit var sharedPref: SharedPreferences

    private val extraChangePin = "isChangePin"

    lateinit var pinCode: String

    private lateinit var defaultPinCode: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_pass)
        pinCodePrefKey = getString(R.string.key_pin_shared_key)
        pinPassSharedPreferences = getString(R.string.key_pin_shared_pref)
        //Default Pin is aaaa
        defaultPinCode = getString(R.string.key_pin_default_pin)
        val isChangePinActive = intent.getBooleanExtra(extraChangePin, true)
        sharedPref = getSharedPreferences(pinPassSharedPreferences, Context.MODE_PRIVATE) ?: return
        pinCode = sharedPref.getString(pinCodePrefKey, defaultPinCode)!!

        if (!isChangePinActive) {
            app = this.application as CradleApplication
            if (pinCode == defaultPinCode) {
                Toast.makeText(this, getString(R.string.pin_not_set), Toast.LENGTH_LONG).show()
                app.pinPassActivityFinished()
                app.logOutofSession()
                val intent = Intent(this@PinPassActivity, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }

            app.pinPassActivityStarted()
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
                val intent = Intent(this@PinPassActivity, DashBoardActivity::class.java)
                startActivity(intent)
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
            headerLine.text = getString(R.string.change_pin_act_first_enter)
            var confirmPin = false
            var tempPin = defaultPinCode
            passText.doAfterTextChanged {
                if (!confirmPin && passText.text.toString().length == 4) {
                    tempPin = passText.text.toString()
                    confirmPin = true
                    headerLine.text = getString(R.string.change_pin_act_second_enter)
                    passText.setText("")
                } else if (confirmPin && passText.text.toString().length == 4) {
                    if (passText.text.toString() == tempPin) {
                        with(sharedPref.edit()) {
                            putString(pinCodePrefKey, tempPin)
                            apply()
                        }
                        Toast.makeText(this, getString(R.string.change_pin_act_pin_saved), Toast.LENGTH_LONG).show()
                        val intent = Intent(this@PinPassActivity, DashBoardActivity::class.java)
                        startActivity(intent)
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
                    finish()
                } else if (passText.text.toString().length == 4) {
                    incorrectText.text = getString(R.string.pinpass_incorrect_password)
                    passText.setText("")
                }
            }
        }
    }

    override fun onBackPressed() {
        //Do not allow user to leave this screen until password is entered or app exited
    }
}
