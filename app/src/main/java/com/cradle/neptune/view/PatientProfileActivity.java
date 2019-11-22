package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Patient.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static com.cradle.neptune.view.DashBoardActivity.READING_ACTIVITY_DONE;


public class PatientProfileActivity extends AppCompatActivity {

    TextView patientID;
    TextView patientName;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    TextView patientHouse;
    TextView patientZone;
    TextView patientTank;
    TextView pregnant;
    TextView gestationalAge;
    TextView gestationalAgeUnit;

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
        patientHouse = (TextView) findViewById(R.id.patientHouseNum);
        patientZone = (TextView) findViewById(R.id.patientZone);
        patientTank = (TextView) findViewById(R.id.patientTank);
        pregnant = (TextView) findViewById(R.id.textView20);
        gestationalAge = (TextView) findViewById(R.id.textView45);
        gestationalAgeUnit = (TextView) findViewById(R.id.textView46);

        readingRecyclerview = findViewById(R.id.readingRecyclerview);

        currPatient = (Patient) getIntent().getSerializableExtra("key");
        populatePatientInfo(currPatient);
        setupReadingsRecyclerView();
        setupCreatePatientReadingButton();
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
        patientHouse.setText(patient.houseNumber);
        patientZone.setText(patient.zone);
        patientTank.setText(patient.tankNo);
        if (patient.isPregnant) {
            pregnant.setText("Yes");
        } else {
            pregnant.setText("No");
        }
        gestationalAge.setText(patient.gestationalAgeValue);
        if (patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS) {
            gestationalAgeUnit.setText("Weeks");
        } else {
            gestationalAgeUnit.setText("Months");
        }

    }

    private void setupCreatePatientReadingButton() {
        Button createButton = findViewById(R.id.newPatientReadingButton);

        List<Reading> readings = readingManager.getReadings(this);
        Collections.sort(readings, new Reading.ComparatorByDateReverse());
        boolean readingFound = false;
        Reading latestReading = new Reading();

        for (Reading reading : readings) {
            Patient patient = reading.patient;
            if (patient.patientId.equals(currPatient.patientId)) {
                latestReading = reading;
                readingFound = true;
                break;
            }
        }

        //button only works if readings exist, which it always should
        if(readingFound) {
            long readingID = latestReading.readingId;
            createButton.setOnClickListener(v -> {
                Intent intent = ReadingActivity.makeIntentForNewReadingExistingPatient(PatientProfileActivity.this, readingID);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            });
        }
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
                askToDeleteReading(readingId);
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

    private void askToDeleteReading(long readingId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setMessage("Delete reading?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog1, whichButton) -> {
                    readingManager.deleteReadingById(this, readingId);
                    updateUi();
                })
                .setNegativeButton(android.R.string.no, null);
        dialog.show();
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