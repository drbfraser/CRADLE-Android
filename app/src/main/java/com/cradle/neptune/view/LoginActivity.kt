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
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.manager.network.VolleyRequestManager
import com.cradle.neptune.manager.network.VolleyRequests
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.utilitiles.VolleyUtil
import java.util.ArrayList
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    @Inject
    lateinit var readingManager: ReadingManager
    @Inject
    lateinit var healthCentreManager: HealthCentreManager
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var urlManager: UrlManager
    @Inject
    lateinit var patientManager: PatientManager

    lateinit var volleyRequestManager: VolleyRequestManager

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MyApp).appComponent.inject(this)
        // no need to load anything if already logged in.
        checkSharedPrefForLogin()
        volleyRequestManager = VolleyRequestManager(this)
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

            volleyRequestManager.authenticateTheUser(emailET.text.toString(), passwordET.text.toString(),
            object : VolleyRequests.SuccessFullCallBack{
                override fun isSuccessFull(isSuccessFull: Boolean) {
                    progressDialog.cancel()
                    if (isSuccessFull) {
                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT)
                            .show()
                        volleyRequestManager.fetchAllMyPatientsFromServer()
                        getAllTheHealthFacilities()
                    } else {
                        errorText.visibility = View.VISIBLE
                    }
                }
            })
        }
    }

    /**
     * make a [JsonObjectRequest] to fetch all the healthfacility for the user
     */
    private fun getAllTheHealthFacilities() {
        val jsonArrayRequest = VolleyUtil.makeJsonArrayRequest(Request.Method.GET,
            urlManager.healthFacilities, null, Response.Listener {
                val facilities = ArrayList<HealthFacility>()
                for (i in 0 until it.length()) {
                    facilities.add(HealthFacility.unmarshal(it[i] as JsonObject))
                }
                healthCentreManager.addAll(facilities)
            }, Response.ErrorListener {
                Log.d(TAG, it.toString())
            }, sharedPreferences
        )

        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add(jsonArrayRequest)
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
        private const val FETCH_PATIENT_TIMEOUT_MS = 150000
        private val TAG = LoginActivity::class.java.canonicalName

    }
}
