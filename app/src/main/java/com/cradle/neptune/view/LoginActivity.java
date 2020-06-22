package com.cradle.neptune.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.database.HealthFacilityEntity;
//import com.cradle.neptune.database.ParsePatientInformationAsyncTask;
import com.cradle.neptune.manager.UrlManager;
import com.cradle.neptune.model.*;

import com.cradle.neptune.manager.HealthCentreManager;
import com.cradle.neptune.manager.ReadingManager;
import kotlin.Pair;
import kotlin.Unit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public static final String TWILIO_PHONE_NUMBER = "16042298878";
    public static int loginBruteForceAttempts = 3;
    @Inject
    ReadingManager readingManager;
    @Inject
    HealthCentreManager healthCentreManager;
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    UrlManager urlManager;

    /**
     * makes the volley call to get all the  past readings from this user.
     * Since Volley runs on its own thread, its okay for UI or activity to change as long as
     * we are not referrencing them.
     *
     * @param token token for the user
     */
    public static void getAllMyPatients(String token, ReadingManager readingManager, UrlManager urlManager, Context context) {
        JsonRequest<JSONArray> jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, urlManager.getAllPatientInfo(),
                null, response -> {

            ApiKt.legacyUnmarshallAllInfoAsync(response, result -> {
                // Populate the database with the patients and readings.
                for (Pair<Patient, ? extends List<Reading>> pair : result) {
                    for (Reading reading : pair.getSecond()) {
                        readingManager.addReadingAsync(pair.getFirst(), reading);
                    }
                }

                // FIXME: Doesn't send the notification once finished like the original does.
                return Unit.INSTANCE;
            });
//            ParsePatientInformationAsyncTask parsePatientInformationAsyncTask =
//                    new ParsePatientInformationAsyncTask(response, context.getApplicationContext(), readingManager);
//            parsePatientInformationAsyncTask.execute();
        }, error -> {
            Log.d("bugg", "failed: " + error);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context.getApplicationContext());
            notificationManager.cancel(PatientDownloadingNotificationID);
            //let user know we failed getting the patients info // maybe due to timeout etc?
            buildNotification(context.getString(R.string.app_name), "Failed to download Patients information...", PatientDownloadFailNotificationID, context);
            try {
                if (error.networkResponse != null) {
                    String json = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers));
                    Log.d("bugg1", json + "  " + error.networkResponse.statusCode);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
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
        Toast.makeText(context, "Downloading patient's information, Check the status bar for progress.", Toast.LENGTH_LONG).show();
        //timeout to 15 second if there are alot of patients
        jsonArrayRequest.setRetryPolicy(new DefaultRetryPolicy(150000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_TIMEOUT_MS));
        RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
        queue.add(jsonArrayRequest);
        buildNotification(context.getString(R.string.app_name), "Downloading Patients....", PatientDownloadingNotificationID, context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ((MyApp) getApplication()).getAppComponent().inject(this);

        checkSharedPrefForLogin();
        setupLogin();
    }

    private void checkSharedPrefForLogin() {
        String email = sharedPreferences.getString(LOGIN_EMAIL, DEFAULT_EMAIL);
        int password = sharedPreferences.getInt(LOGIN_PASSWORD, DEFAULT_PASSWORD);
        if (!email.equals(DEFAULT_EMAIL) && password != DEFAULT_PASSWORD) {
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
            //loginBruteForceAttempts--;
            ProgressDialog progressDialog = getProgressDialog();
            RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("email", emailET.getText());
                jsonObject.put("password", passwordET.getText());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, urlManager.getAuthentication(), jsonObject, response -> {
                progressDialog.cancel();
                //put it into sharedpref for offline login.
                saveUserNamePasswordSharedPref(emailET.getText().toString(), passwordET.getText().toString());
                Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                try {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(TOKEN, response.getString(TOKEN));
                    editor.putString(USER_ID, response.getString("userId"));
                    editor.apply();
                    String token = response.get(TOKEN).toString();
                    getAllMyPatients(token, readingManager, urlManager, this);
                    // only for testing
                    populateHealthFacilities();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                startIntroActivity();
            }, error -> {
                errorText.setVisibility(View.VISIBLE);
                progressDialog.cancel();
                if (error != null) {
                    error.printStackTrace();
                    if (error.networkResponse != null) {
                        Log.d("bugg", error.networkResponse.statusCode + "");
                    }
                    Log.d("bugg", error.getMessage() + "local: " + error.getLocalizedMessage());
                }
            });
            queue.add(jsonObjectRequest);
        });
    }

    private void populateHealthFacilities() {

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.GET, urlManager.getHealthFacility(), null, response -> {
            try {
                List<HealthFacilityEntity> healthFacilityEntities = new ArrayList<>();
                //adding our default one for twilio
                HealthFacilityEntity hf = new HealthFacilityEntity(UUID.randomUUID().toString(),
                        "Neptune's five star care", "Planet Neptune", TWILIO_PHONE_NUMBER, "Default TWILIO", "TW");
                hf.setUserSelected(true);
                healthFacilityEntities.add(hf);

                for (int i = 0; i < response.length(); i++) {
                    JSONObject jsonObject = response.getJSONObject(i);
                    String id = UUID.randomUUID().toString();
                    //todo get hcf id from the server, currently server doesnt have one
                    HealthFacilityEntity healthFacilityEntity = new HealthFacilityEntity
                            (id, jsonObject.getString("healthFacilityName"),
                                    jsonObject.getString("location"),
                                    jsonObject.getString("healthFacilityPhoneNumber"),
                                    jsonObject.getString("about"),
                                    jsonObject.getString("facilityType"));

                    healthFacilityEntities.add(healthFacilityEntity);
                }
                healthCentreManager.addAllAsync(healthFacilityEntities);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, error -> Log.d("bugg", "error: " + error.toString())) {
            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() {
                HashMap<String, String> headers = new HashMap<>();
                //headers.put("Content-Type", "application/json");
                headers.put(LoginActivity.AUTH, "Bearer " + sharedPreferences.getString(TOKEN, ""));
                return headers;
            }
        };
        RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
        queue.add(jsonArrayRequest);
    }

    private void saveUserNamePasswordSharedPref(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
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
