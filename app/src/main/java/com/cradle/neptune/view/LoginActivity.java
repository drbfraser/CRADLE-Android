package com.cradle.neptune.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Settings;

import org.json.JSONException;
import org.json.JSONObject;


public class LoginActivity extends AppCompatActivity {

    public static final String LOGIN_EMAIL = "loginEmail";
    public static final String LOGIN_PASSWORD = "loginPassword";
    public static final String TOKEN = "token";
    public static final String AUTH = "Authorization";
    public static final String USER_ID = "userId";
    public static final  String DEFAULT_EMAIL="";
    public static final  int DEFAULT_PASSWORD =-1;
    public static final  String DEFAULT_TOKEN = null;
    public static final String AUTH_PREF ="authSharefPref";
    public static int loginBruteForceAttempts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        checkSharedPrefForLogin();
        setupLogin();

        loginBruteForceAttempts = 3;
    }

    private void checkSharedPrefForLogin() {
        SharedPreferences sharedPref = this.getSharedPreferences(AUTH_PREF,Context.MODE_PRIVATE);
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

            if (emailET.getText().equals("")) {
                Toast.makeText(LoginActivity.this, "Empty Email", Toast.LENGTH_SHORT).show();
                return;
            }
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
                //put it into sharedpress for offline login.
                saveUserNamePasswordSharedPref(emailET.getText().toString(), passwordET.getText().toString());
                Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_LONG).show();
                try {
                    SharedPreferences sharedPref = LoginActivity.this.getSharedPreferences(AUTH_PREF,Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString(TOKEN, response.getString(TOKEN));
                    editor.putString(USER_ID,response.getString("userId"));
                    editor.apply();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                startIntroActivity();
            }, error -> {
                Toast.makeText(LoginActivity.this, " Invalid credentials", Toast.LENGTH_LONG).show();
                errorText.setVisibility(View.VISIBLE);
                progressDialog.cancel();
            });
            queue.add(jsonObjectRequest);
        });
    }

    private void saveUserNamePasswordSharedPref(String email, String password) {
        SharedPreferences sharedPref = LoginActivity.this.getSharedPreferences(AUTH_PREF,Context.MODE_PRIVATE);
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
