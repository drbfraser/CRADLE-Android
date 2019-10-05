package com.cradletrial.cradlevhtapp.view;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.cradletrial.cradlevhtapp.R;

public class DashBoardActivity extends AppCompatActivity  implements View.OnClickListener {

    View patientCard;
    View helpCard ;
    View referralCard ;
    View statCard;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);
        setupOnClickListner();

    }
    private void setupOnClickListner(){
         patientCard = findViewById(R.id.patientLayout);
         helpCard = findViewById(R.id.helpLayout);
         referralCard = findViewById(R.id.readingLayout);
         statCard = findViewById(R.id.statLayout);
        patientCard.setOnClickListener(this::onClick);
        helpCard.setOnClickListener(this::onClick);
        statCard.setOnClickListener(this);
        referralCard.setOnClickListener(this::onClick);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.readingLayout:
                startActivity(ReadingsListActivity.makeIntent(this));
                break;
            case R.id.patientLayout:
                startActivity(PatientsActivity.makeIntent(this));
                break;
            case R.id.statLayout:
                startActivity(UploadActivity.makeIntent(this));
                break;
            case R.id.helpLayout:
                startActivity(HelpActivity.makeIntent(this));
                break;

        }
    }
}
