package com.cradletrial.cradlevhtapp.view.ui;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.view.IntroActivity;
import com.cradletrial.cradlevhtapp.view.SplashActivity;

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
                Intent intent = new Intent(LoginActivity.this, IntroActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
