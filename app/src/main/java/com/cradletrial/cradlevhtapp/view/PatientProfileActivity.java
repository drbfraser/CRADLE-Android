package com.cradletrial.cradlevhtapp.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingManager;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.viewmodel.ReadingRecyclerViewAdapter;
import com.cradletrial.cradlevhtapp.viewmodel.ReadingViewAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static com.cradletrial.cradlevhtapp.view.ReadingsListActivity.READING_ACTIVITY_DONE;


public class PatientProfileActivity extends AppCompatActivity {

    TextView patientID;
    TextView patientName;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    RecyclerView readingRecyclerview;

    // Data Model
    @Inject
    ReadingManager readingManager;
    @Inject
    SharedPreferences sharedPreferences;
    // ..inject this even if not needed because it forces it to load at startup and initialize.
    @Inject
    Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);

        android.support.v7.preference.PreferenceManager.setDefaultValues(
                this,R.xml.preferences, false);

        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        patientID = (TextView) findViewById(R.id.patientId);
        patientName = (TextView) findViewById(R.id.patientName);
        patientAge = (TextView) findViewById(R.id.patientAge);
        patientSex = (TextView) findViewById(R.id.patientSex);
        villageNo = (TextView) findViewById(R.id.patientVillage);
        readingRecyclerview = findViewById(R.id.readingRecyclerview);

        Patient mPatient = (Patient) getIntent().getSerializableExtra("key");
        populatePatientInfo(mPatient);
        setupReadingsRecyclerView();
        if(getSupportActionBar() != null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.patient_summary);
        }

    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    void populatePatientInfo(Patient patient)
    {
        patientID.setText(patient.patientId);
        patientName.setText(patient.patientName);
        patientAge.setText(patient.ageYears.toString());
        patientSex.setText(patient.patientSex.toString());
        villageNo.setText(patient.villageNumber);

    }

    private void setupReadingsRecyclerView() {

        // Improve performance: size of each entry does not change.

        // use linear layout
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        readingRecyclerview.setLayoutManager(layoutManager);
        readingRecyclerview.setNestedScrollingEnabled(false);
        ReadingRecyclerViewAdapter  listAdapter;
        // get content & sort
        List<Reading> readings = readingManager.getReadings(this);
        Collections.sort(readings, new Reading.ComparatorByDateReverse());

        // set adapter
        listAdapter = new ReadingRecyclerViewAdapter(readings,this);

        readingRecyclerview.setAdapter(listAdapter);
        listAdapter.notifyDataSetChanged();
        Log.d("bugg","LIST SIZE: "+ readings.size());
    }
}