package com.cradle.neptune.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
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
            getSupportActionBar().setTitle("Help");
        }
    }

    private void setupOnCLickMethods() {
        CardView postercard = findViewById(R.id.communityPosterView);
        postercard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent =new Intent(EducationActivity.this,PdfViewActivity.class);
                intent.putExtra("poster","education_community_poster.pdf");
                startActivity(intent);
            }
        });

        CardView videoCard = findViewById(R.id.videoCardview);
        videoCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(HelpActivity.makeIntent(EducationActivity.this));
            }
        });

        CardView clinicPoster = findViewById(R.id.clinicPosterView);
        clinicPoster.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent =new Intent(EducationActivity.this,PdfViewActivity.class);
                intent.putExtra("poster","education_clinic_poster.pdf");
                startActivity(intent);
            }
        });
    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
