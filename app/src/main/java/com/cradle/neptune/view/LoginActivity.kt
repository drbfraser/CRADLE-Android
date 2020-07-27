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
import com.cradle.neptune.manager.VolleyRequestManager
import javax.inject.Inject

class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var volleyRequestManager: VolleyRequestManager

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MyApp).appComponent.inject(this)
        // no need to load anything if already logged in.
        checkSharedPrefForLogin()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setupLogin()
    }

    private fun checkSharedPrefForLogin() {
        val email = sharedPreferences.getString(
            LOGIN_EMAIL,
            DEFAULT_EMAIL
        )
        val password = sharedPreferences.getInt(LOGIN_PASSWORD, DEFAULT_PASSWORD)
        if (email != DEFAULT_EMAIL && password != DEFAULT_PASSWORD) {
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

            volleyRequestManager.authenticateTheUser(
                emailET.text.toString(),
                passwordET.text.toString()
            ) { isSuccessFul ->
                progressDialog.cancel()
                if (isSuccessFul) {
                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT)
                        .show()

                    volleyRequestManager.fetchAllMyPatientsFromServer()
                    volleyRequestManager.getAllHealthFacilities()

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

    companion object {
        const val LOGIN_EMAIL = "loginEmail"
        const val LOGIN_PASSWORD = "loginPassword"
        const val TOKEN = "token"
        const val AUTH = "Authorization"
        const val USER_ID = "userId"
        const val DEFAULT_EMAIL = ""
        const val DEFAULT_PASSWORD = -1
        val DEFAULT_TOKEN: String? = null
        private val TAG = LoginActivity::class.java.canonicalName
    }
}
