package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

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
    List<Reading> patientReadings;
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

        PreferenceManager.setDefaultValues(
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

        getPatientReadings();
        setupReadingsRecyclerView();
        setupCreatePatientReadingButton();
        setupLineChart();

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

    @Override
    protected void onResume() {
        super.onResume();
        getPatientReadings();
        setupLineChart();
        setupReadingsRecyclerView();

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
        if(patient.drugHistoryList!=null && !patient.drugHistoryList.isEmpty()) {
            TextView drugHistroy = findViewById(R.id.drugHistroyTxt);
            drugHistroy.setText(patient.drugHistoryList.get(0));

        }
        if(patient.medicalHistoryList!=null && !patient.medicalHistoryList.isEmpty()) {

            TextView medHistory = findViewById(R.id.medHistoryText);
            medHistory.setText(patient.medicalHistoryList.get(0));
        }
    }

    private void getPatientReadings() {
        List<Reading> readings = readingManager.getReadings(this);
        Collections.sort(readings, new Reading.ComparatorByDateReverse());
        patientReadings= new ArrayList<>();
        for (Reading reading : readings) {
            Patient patient = reading.patient;
            if (patient.patientId.equals(currPatient.patientId)) {
                patientReadings.add(reading);
            }
        }
    }

    private void setupLineChart() {
        LineChart lineChart = findViewById(R.id.patientLineChart);
        CardView lineChartCard = findViewById(R.id.patientLineChartCard);
        lineChartCard.setVisibility(View.VISIBLE);

        List<Entry> sBPs = new ArrayList<>();
        List<Entry> dBPs = new ArrayList<>();
        List<Entry> bPMs = new ArrayList<>();

        //put data sets in chronological order
        int index = patientReadings.size();
        for (Reading reading : patientReadings) {
            sBPs.add(0, new Entry(index, reading.bpSystolic));
            dBPs.add(0, new Entry(index, reading.bpDiastolic));
            bPMs.add(0, new Entry(index, reading.heartRateBPM));
            index--;
        }


        LineDataSet sBPDataSet = new LineDataSet(sBPs, "Systolic BP");
        LineDataSet dBPDataSet = new LineDataSet(dBPs, "Diastolic BP");
        LineDataSet bPMDataSet = new LineDataSet(bPMs, "Heart Rate BPM");

        sBPDataSet.setColor(getResources().getColor(R.color.purple));
        sBPDataSet.setCircleColor(getResources().getColor(R.color.purple));

        dBPDataSet.setColor(getResources().getColor(R.color.colorAccent));
        dBPDataSet.setCircleColor(getResources().getColor(R.color.colorAccent));

        bPMDataSet.setColor(getResources().getColor(R.color.orange));
        bPMDataSet.setCircleColor(getResources().getColor(R.color.orange));

        bPMDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dBPDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        sBPDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        lineChart.setDrawBorders(false);
        lineChart.setDrawGridBackground(false);
        lineChart.getAxisRight().setDrawLabels(false);
        lineChart.getAxisRight().setDrawGridLines(false);
        lineChart.getXAxis().setDrawGridLines(true);
        lineChart.getAxisLeft().setDrawGridLines(true);

        LineData lineData = new LineData(sBPDataSet, dBPDataSet, bPMDataSet);

        lineData.setHighlightEnabled(false);

        lineChart.getXAxis().setDrawAxisLine(true);
        lineChart.setData(lineData);
        lineChart.getXAxis().setEnabled(false);
        lineChart.getDescription().setText("Cardiovascular Data from last " + patientReadings.size() + " readings");
        lineChart.invalidate();

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

        // set adapter
        listAdapter = new ReadingRecyclerViewAdapter(patientReadings);

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
        setupLineChart();
    }

}