package com.cradle.neptune.view

import android.app.ProgressDialog
import android.content.Context
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
import androidx.core.app.NotificationManagerCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.JsonRequest
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.model.HealthFacility
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.manager.PatientManager
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.Patient
import com.cradle.neptune.model.Reading
import com.cradle.neptune.utilitiles.NotificationUtils
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.HashMap
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
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
        val password = sharedPreferences.getInt(
            LOGIN_PASSWORD,
            DEFAULT_PASSWORD
        )
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
        val loginbutton =
            findViewById<Button>(R.id.loginButton)
        loginbutton.setOnClickListener { v: View? ->
            if (loginBruteForceAttempts <= 0) {
                startIntroActivity()
                return@setOnClickListener
            }
            // loginBruteForceAttempts--;
            val progressDialog = progressDialog
            val queue = Volley.newRequestQueue(MyApp.getInstance())
            val jsonObject = JSONObject()
            try {
                jsonObject.put("email", emailET.text)
                jsonObject.put("password", passwordET.text)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            val jsonObjectRequest = JsonObjectRequest(
                Request.Method.POST,
                urlManager.authentication,
                jsonObject,
                Response.Listener { response: JSONObject ->
                    progressDialog.cancel()
                    // put it into sharedpref for offline login.
                    saveUserNamePasswordSharedPref(
                        emailET.text.toString(),
                        passwordET.text.toString()
                    )
                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT)
                        .show()
                    try {
                        val editor = sharedPreferences.edit()
                        editor.putString(
                            TOKEN,
                            response.getString(TOKEN)
                        )
                        editor.putString(
                            USER_ID,
                            response.getString("userId")
                        )
                        editor.apply()
                        val token =
                            response[TOKEN].toString()
                        getAllMyPatients(
                            token,
                            readingManager,
                            urlManager,
                            patientManager,
                            this
                        )
                        // only for testing
                        populateHealthFacilities()
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                    startIntroActivity()
                },
                Response.ErrorListener { error: VolleyError? ->
                    errorText.visibility = View.VISIBLE
                    progressDialog.cancel()
                    if (error != null) {
                        error.printStackTrace()
                        if (error.networkResponse != null) {
                            Log.d(
                                "bugg",
                                error.networkResponse.statusCode.toString() + ""
                            )
                        }
                        Log.d(
                            "bugg",
                            error.message + "local: " + error.localizedMessage
                        )
                    }
                }
            )
            queue.add(jsonObjectRequest)
        }
    }

    private fun populateHealthFacilities() {
        val jsonArrayRequest: JsonArrayRequest = object : JsonArrayRequest(
            Method.GET,
            urlManager.healthFacility,
            null,
            Response.Listener { response: JSONArray ->
                GlobalScope.launch {
                    try {
                        val healthFacilities: MutableList<HealthFacility> =
                            ArrayList()
                        // adding our default one for twilio
                        val hf = HealthFacility(
                            UUID.randomUUID().toString(),
                            "Neptune's five star care",
                            "Planet Neptune",
                            "TWILIO_PHONE_NUMBER",
                            "Default TWILIO",
                            "TW"
                        )
                        hf.isUserSelected = true
                        healthFacilities.add(hf)
                        for (i in 0 until response.length()) {
                            val jsonObject = response.getJSONObject(i)
                            val id = UUID.randomUUID().toString()
                            // todo get hcf id from the server, currently server doesnt have one
                            val healthFacility =
                                HealthFacility(
                                    id, jsonObject.getString("healthFacilityName"),
                                    jsonObject.getString("location"),
                                    jsonObject.getString("healthFacilityPhoneNumber"),
                                    jsonObject.getString("about"),
                                    jsonObject.getString("facilityType")
                                )
                            healthFacilities.add(healthFacility)
                        }

                        healthCentreManager.addAll(healthFacilities)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            },
            Response.ErrorListener { error: VolleyError ->
                Log.d(
                    "bugg",
                    "error: $error"
                )
            }
        ) {
            /**
             * Passing some request headers
             */
            override fun getHeaders(): Map<String, String> {
                val headers =
                    HashMap<String, String>()
                // headers.put("Content-Type", "application/json");
                headers[AUTH] = "Bearer " + sharedPreferences.getString(TOKEN, "")
                return headers
            }
        }
        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add(jsonArrayRequest)
    }

    private fun saveUserNamePasswordSharedPref(
        email: String,
        password: String
    ) {
        val editor = sharedPreferences.edit()
        editor.putString(LOGIN_EMAIL, email)
        editor.putInt(LOGIN_PASSWORD, password.hashCode())
        editor.apply()
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
        const val AUTH_PREF = "authSharefPref"
        const val loginBruteForceAttempts = 3
        const val FETCH_PATIENT_TIMEOUT_MS = 150000

        /**
         * makes the volley call to get all the  past readings from this user.
         * Since Volley runs on its own thread, its okay for UI or activity to change as long as
         * we are not referrencing them.
         *
         * @param token token for the user
         */
        fun getAllMyPatients(
            token: String,
            readingManager: ReadingManager,
            urlManager: UrlManager,
            patientManager: PatientManager,
            context: Context
        ) {
            val jsonArrayRequest: JsonRequest<JSONArray> =
                object : JsonArrayRequest(
                    Method.GET,
                    urlManager.allPatientInfo,
                    null,
                    Response.Listener { response: JSONArray ->
                        try {
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }

                        GlobalScope.launch(Dispatchers.IO) {
                            val patientList = ArrayList<Patient>()
                            val readingList = ArrayList<Reading>()
                            for (i in 0 until response.length()) {
                                // get all the readings
                                patientList.add(Patient.unmarshal(response[i] as JsonObject))
                                val readingArray = (response[i] as JsonObject).getJSONArray("readings")
                                for (j in 0 until readingArray.length()) {
                                    readingList.add(Reading.unmarshal(readingArray[j] as JsonObject))
                                }
                            }
                            patientManager.addAll(patientList)
                            readingManager.addAllReadings(readingList)
                        }
                    },
                    Response.ErrorListener { error: VolleyError ->
                        Log.d("bugg", "failed: $error")
                        val notificationManager =
                            NotificationManagerCompat.from(context.applicationContext)
                        notificationManager.cancel(NotificationUtils.PatientDownloadingNotificationID)
                        // let user know we failed getting the patients info // maybe due to timeout etc?
                        NotificationUtils.buildNotification(
                            context.getString(R.string.app_name),
                            "Failed to download Patients information...",
                            NotificationUtils.PatientDownloadFailNotificationID,
                            context
                        )
                        try {
                            if (error.networkResponse != null) {
                                val json = String(
                                    error.networkResponse.data,
                                    HttpHeaderParser.parseCharset(error.networkResponse.headers) as Charset
                                )
                                Log.d(
                                    "bugg1",
                                    json + "  " + error.networkResponse.statusCode
                                )
                            }
                        } catch (e: UnsupportedEncodingException) {
                            e.printStackTrace()
                        }
                    }
                ) {
                    /**
                     * Passing some request headers
                     */
                    override fun getHeaders(): Map<String, String> {
                        val headers =
                            HashMap<String, String>()
                        // headers.put("Content-Type", "application/json");
                        headers[AUTH] = "Bearer $token"
                        return headers
                    }
                }
            Toast.makeText(
                context,
                "Downloading patient's information, Check the status bar for progress.",
                Toast.LENGTH_LONG
            ).show()
            // timeout to 15 second if there are a lot of patients
            jsonArrayRequest.retryPolicy = DefaultRetryPolicy(
                FETCH_PATIENT_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS.toFloat()
            )
            val queue = Volley.newRequestQueue(MyApp.getInstance())
            queue.add(jsonArrayRequest)
            NotificationUtils.buildNotification(
                context.getString(R.string.app_name),
                "Downloading Patients....",
                NotificationUtils.PatientDownloadingNotificationID,
                context
            )
        }
    }
}
