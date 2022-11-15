package com.cradleplatform.neptune.view

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.cradleplatform.neptune.CradleApplication
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.LoginManager
import kotlinx.coroutines.launch
import javax.inject.Inject

class PinPassActivity : AppCompatActivity() {
    lateinit var app: CradleApplication
    @Inject
    lateinit var loginManager: LoginManager

    //Temporary until function fully implemented
    val pinCode = "1234"

    //TODO: Make this Activity also be able to set up the new PIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_pass)
        app = this.application as CradleApplication

        setUpButtons()
        setUpPIN()
    }

    private fun setUpButtons() {
        val forgotButton = findViewById<Button>(R.id.pinPassForgotButton)
        forgotButton.setOnClickListener {
            app.pinPassActivityFinished()
            app.appCoroutineScope.launch {
                loginManager.logout()
            }
            val intent = Intent(this@PinPassActivity, LoginActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setUpPIN() {
        val passText = findViewById<EditText>(R.id.pinPassEdit)
        val incorrectText = findViewById<TextView>(R.id.pinPassIncorrectText)

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

    override fun onBackPressed() {
        //Do not allow user to leave this screen until password is entered or app exited
    }
}
