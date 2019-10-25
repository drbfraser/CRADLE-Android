package com.cradletrial.cradlevhtapp.view.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.view.IntroActivity;
import com.cradletrial.cradlevhtapp.view.SplashActivity;
import com.cradletrial.cradlevhtapp.view.ui.network_volley.MultipartRequest;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupLogin();
    }

    private void setupLogin() {
        Button loginbuttoon = findViewById(R.id.loginButton);
        loginbuttoon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //todo change it so that we authenticate the user first and than go to the intro page.

                // add to volley queue
                RequestQueue queue = Volley.newRequestQueue(MyApp.getInstance());
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("email","email");
                    jsonObject.put("password","*****");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, Settings.authServerUrl, jsonObject, response -> {
                    Toast.makeText(LoginActivity.this,"LOGIN",Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(LoginActivity.this, IntroActivity.class);
                    startActivity(intent);
                    finish();
                }, error -> {
                    Toast.makeText(LoginActivity.this,error+" unable to log in",Toast.LENGTH_LONG).show();
                });
                queue.add(jsonObjectRequest);
            }
        });
    }
}
