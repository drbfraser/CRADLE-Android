package com.cradletrial.cradlevhtapp.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingManager;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.viewmodel.ReadingRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static com.cradletrial.cradlevhtapp.view.DashBoardActivity.READING_ACTIVITY_DONE;


public class PatientProfileActivity extends AppCompatActivity {

    TextView patientID;
    TextView patientName;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    RecyclerView readingRecyclerview;
    Patient currPatient;
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
                this, R.xml.preferences, false);

        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        patientID = (TextView) findViewById(R.id.patientId);
        patientName = (TextView) findViewById(R.id.patientName);
        patientAge = (TextView) findViewById(R.id.patientAge);
        patientSex = (TextView) findViewById(R.id.patientSex);
        villageNo = (TextView) findViewById(R.id.patientVillage);
        readingRecyclerview = findViewById(R.id.readingRecyclerview);

        currPatient = (Patient) getIntent().getSerializableExtra("key");
        populatePatientInfo(currPatient);
        setupReadingsRecyclerView();
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.patient_summary);
        }

    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    void populatePatientInfo(Patient patient) {
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
        ReadingRecyclerViewAdapter listAdapter;
        // get content & sort
        List<Reading> readings = readingManager.getReadings(this);
        Collections.sort(readings, new Reading.ComparatorByDateReverse());
        List<Reading> myReadings = new ArrayList<>();
        for (Reading reading : readings) {
            Patient patient = reading.patient;
            if (patient.patientId.equals(currPatient.patientId)) {
                myReadings.add(reading);
            }
        }        // set adapter
        listAdapter = new ReadingRecyclerViewAdapter(myReadings);

        listAdapter.setOnClickElementListener(new ReadingRecyclerViewAdapter.OnClickElement() {
            @Override
            public void onClick(long readingId) {
                Intent intent = ReadingActivity.makeIntentForEdit(PatientProfileActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }

            @Override
            public boolean onLongClick(long readingId) {
                //askToDeleteReading(readingId);
                return true;
            }

            @Override
            public void onClickRecheckReading(long readingId) {
                Intent intent = ReadingActivity.makeIntentForRecheck(PatientProfileActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }
        });
        readingRecyclerview.setAdapter(listAdapter);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == READING_ACTIVITY_DONE) {
            updateUi();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateUi() {
        // setupEmptyState();
        setupReadingsRecyclerView();
    }

}