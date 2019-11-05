package com.cradle.neptune.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.Decrypter;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    public static final String LOGIN_EMAIL = "loginEmail";
    public static final String LOGIN_PASSWORD = "loginPassword";
    private int loginCount = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Test();
       // checkSharedPrefForLogin();

        //setupLogin();
    }

    private void Test() {
        Decrypter decrypter = null;
        try {
            decrypter = new Decrypter(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void checkSharedPrefForLogin() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        String email = sharedPref.getString(LOGIN_EMAIL, "");
        String password = sharedPref.getString(LOGIN_PASSWORD, "");
        if (!email.equals("") && !password.equals("")) {
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

            //todo remove this from final version
            if (loginCount <= 0) {
                startIntroActivity();
                return;
            }
            loginCount--;
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
                Toast.makeText(LoginActivity.this, "LOGIN", Toast.LENGTH_LONG).show();
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
        SharedPreferences sharedPref = LoginActivity.this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(LOGIN_EMAIL, email);
        editor.putString(LOGIN_PASSWORD, password);
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
