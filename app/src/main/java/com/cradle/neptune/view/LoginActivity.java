package com.cradle.neptune.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.model.UrineTestResult;
import com.cradle.neptune.utilitiles.DateUtil;
import com.cradle.neptune.utilitiles.ParsePatientInformationAsyncTask;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static com.cradle.neptune.utilitiles.NotificationUtils.PatientDownloadFailNotificationID;
import static com.cradle.neptune.utilitiles.NotificationUtils.PatientDownloadingNotificationID;
import static com.cradle.neptune.utilitiles.NotificationUtils.buildNotification;


public class LoginActivity extends AppCompatActivity {

    public static final String LOGIN_EMAIL = "loginEmail";
    public static final String LOGIN_PASSWORD = "loginPassword";
    public static final String TOKEN = "token";
    public static final String AUTH = "Authorization";
    public static final String USER_ID = "userId";
    public static final String DEFAULT_EMAIL = "";
    public static final int DEFAULT_PASSWORD = -1;
    public static final String DEFAULT_TOKEN = null;
    public static final String AUTH_PREF = "authSharefPref";
    public static int loginBruteForceAttempts =3;

    @Inject
    ReadingManager readingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ((MyApp) getApplication()).getAppComponent().inject(this);

        checkSharedPrefForLogin();
        setupLogin();
    }

    private void checkSharedPrefForLogin() {
        SharedPreferences sharedPref = this.getSharedPreferences(AUTH_PREF, Context.MODE_PRIVATE);
        String email = sharedPref.getString(LOGIN_EMAIL, DEFAULT_EMAIL);
        int password = sharedPref.getInt(LOGIN_PASSWORD, DEFAULT_PASSWORD);
        if (!email.equals(DEFAULT_EMAIL) && password!=DEFAULT_PASSWORD) {
            startIntroActivity();
        }

    }

    private void startIntroActivity() {
        Intent intent = new Intent(LoginActivity.this, IntroActivity.class);
        startActivity(intent);
        finish();
    }

    private void setupLogin() {
        EditText emailET = findViewById(R.id.emailEditText);
        EditText passwordET = findViewById(R.id.passwordEditText);
        TextView errorText = findViewById(R.id.invalidLoginText);
        Button loginbuttoon = findViewById(R.id.loginButton);

        loginbuttoon.setOnClickListener(v -> {
            if (loginBruteForceAttempts <= 0) {
                startIntroActivity();
                return;
            }
            loginBruteForceAttempts--;
            ProgressDialog progressDialog = getProgressDialog();
            RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("email", emailET.getText());
                jsonObject.put("password", passwordET.getText());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, Settings.authServerUrl, jsonObject, response -> {
                progressDialog.cancel();
                //put it into sharedpref for offline login.
                saveUserNamePasswordSharedPref(emailET.getText().toString(), passwordET.getText().toString());
                Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                try {
                    SharedPreferences sharedPref = LoginActivity.this.getSharedPreferences(AUTH_PREF, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString(TOKEN, response.getString(TOKEN));
                    editor.putString(USER_ID, response.getString("userId"));
                    editor.apply();
                    String token = response.get(TOKEN).toString();
                   getAllMyPatients(token);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                startIntroActivity();
            }, error -> {
                errorText.setVisibility(View.VISIBLE);
                progressDialog.cancel();
            });
            queue.add(jsonObjectRequest);
        });
    }

    /**
     * makes the volley call to get all the  past readings from this user.
     * Since Volley runs on its own thread, its okay for UI or activity to change as long as
     * we are not referrencing them.
     * @param token token for the user
     */
    private void getAllMyPatients(String token) {
        JsonRequest<JSONArray> jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, Settings.patientGetAllInfoByUserIdUrl,
                null, response -> {

            ParsePatientInformationAsyncTask parsePatientInformationAsyncTask =
                    new ParsePatientInformationAsyncTask(response,getApplicationContext(),readingManager);
            parsePatientInformationAsyncTask.execute();
        }, error -> {
            Log.d("bugg","failed: "+ error);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            notificationManager.cancel(PatientDownloadingNotificationID);
            //let user know we failed getting the patients info // maybe due to timeout etc?
            buildNotification(getString(R.string.app_name),"Failed to download Patients information...",PatientDownloadFailNotificationID,this);
        }) {
            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                //headers.put("Content-Type", "application/json");
                headers.put(LoginActivity.AUTH, "Bearer " + token);
                return headers;
            }
        };
        Toast.makeText(this,"Downloading patient's information, Check the status bar for progress.",Toast.LENGTH_LONG).show();
        RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
        queue.add(jsonArrayRequest);
        buildNotification(getString(R.string.app_name),"Downloading Patients....",PatientDownloadingNotificationID,this);
    }

    private void saveUserNamePasswordSharedPref(String email, String password) {
        SharedPreferences sharedPref = LoginActivity.this.getSharedPreferences(AUTH_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(LOGIN_EMAIL, email);
        editor.putInt(LOGIN_PASSWORD, password.hashCode());
        editor.apply();
    }

    private ProgressDialog getProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(LoginActivity.this);
        progressDialog.setTitle("Logging In");
        progressDialog.setCancelable(false);
        progressDialog.show();
        return progressDialog;
    }

}
