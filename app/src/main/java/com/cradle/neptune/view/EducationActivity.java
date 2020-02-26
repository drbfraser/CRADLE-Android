package com.cradle.neptune.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.os.Bundle;
import android.view.View;

import com.cradle.neptune.R;

public class EducationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_education);
        setupOnCLickMethods();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("HELP");
        }
    }

    private void setupOnCLickMethods() {
        CardView postercard = findViewById(R.id.postercardview);
        postercard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        CardView videoCard = findViewById(R.id.videoCardview);
        videoCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(HelpActivity.makeIntent(EducationActivity.this));
            }
        });
    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
