package com.cradle.neptune.view

import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
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
import com.cradle.neptune.net.Success
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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
        val password = sharedPreferences.getInt(LoginManager.PASSWORD_KEY, LoginManager.PASSWORD_SENTINEL)
        if (email != null && password != LoginManager.PASSWORD_SENTINEL) {
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

        loginButton.setOnClickListener { _: View? ->
            val progressDialog = progressDialog
            passwordET.hideKeyboard()

            MainScope().launch {
                val email = emailET.text.toString()
                val password = passwordET.text.toString()
                val result = loginManager.login(email, password)
                progressDialog.cancel()
                if (result is Success) {
                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startIntroActivity()
                } else {
                    errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    private val progressDialog: ProgressDialog
        get() {
            val progressDialog = ProgressDialog(this@LoginActivity)
            progressDialog.setTitle("Logging In")
            progressDialog.setCancelable(false)
            progressDialog.show()
            return progressDialog
        }
}
