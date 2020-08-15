package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.manager.PatientManager;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.manager.UrlManager;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;


import static com.cradle.neptune.view.DashBoardActivity.READING_ACTIVITY_DONE;


public class PatientProfileActivity extends AppCompatActivity {

    static final double WEEKS_IN_MONTH = 4.34524;
    TextView patientID;
    TextView patientName;
    TextView patientDOB;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    TextView patientZone;
    TextView pregnant;
    TextView gestationalAge;
    LinearLayout pregnancyInfoLayout;

    RecyclerView readingRecyclerview;
    Patient currPatient;
    List<Reading> patientReadings;
    // Data Model
//    @Inject
//    ReadingManager readingManager;
    @Inject
    ReadingManager readingManager;
    @Inject
    PatientManager patientManager;
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    UrlManager urlManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_patient_profile);
        ((MyApp) getApplication()).getAppComponent().inject(this);
        initAllFields();
        if (!getLocalPatient()) {
            //not a local patient, might be a child class so we let the child do the init stuff
            return;
        }
        populatePatientInfo(currPatient);

        setupReadingsRecyclerView();
        setupCreatePatientReadingButton();
        setupLineChart();
        setupToolBar();
    }

    void setupToolBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.patient_summary);
        }
    }


    private void initAllFields() {
        patientID = findViewById(R.id.patientId);
        patientName = findViewById(R.id.patientName);
        patientDOB = findViewById(R.id.patientDOB);
        patientAge = findViewById(R.id.patientAge);
        patientSex = findViewById(R.id.patientSex);
        villageNo = findViewById(R.id.patientVillage);
        patientZone = findViewById(R.id.patientZone);
        pregnant = findViewById(R.id.textView20);
        gestationalAge = findViewById(R.id.gestationalAge);
        pregnancyInfoLayout = findViewById(R.id.pregnancyLayout);
        readingRecyclerview = findViewById(R.id.readingRecyclerview);
    }

    boolean getLocalPatient() {
        currPatient = (Patient) getIntent().getSerializableExtra("patient");
        return (currPatient != null);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getLocalPatient()) {
            //not a local patient, might be a child class
            return;
        }
        setupLineChart();
        setupReadingsRecyclerView();

    }

    void populatePatientInfo(Patient patient) {
        patientID.setText(patient.getId());
        patientName.setText(patient.getName());
        if (!Util.stringNullOrEmpty(patient.getDob())) {
            patientDOB.setText(patient.getDob());
        }
        if (!Util.stringNullOrEmpty(patient.getAge() + "")) {
            patientAge.setText(patient.getAge() + "");
        }
        patientSex.setText(patient.getSex().toString());
        if (!Util.stringNullOrEmpty(patient.getVillageNumber())) {
            villageNo.setText(patient.getVillageNumber());
        }
        if (!Util.stringNullOrEmpty(patient.getZone())) {
            patientZone.setText(patient.getZone());
        }
        if (patient.isPregnant()) {
            pregnant.setText("Yes");
            setupGestationalInfo(patient);
        } else {
            pregnant.setText("No");
            pregnancyInfoLayout.setVisibility(View.GONE);
        }

        patient.getDrugHistoryList();
        if (!patient.getDrugHistoryList().isEmpty()) {
            TextView drugHistroy = findViewById(R.id.drugHistroyTxt);
            drugHistroy.setText(patient.getDrugHistoryList().get(0));

        }
        patient.getMedicalHistoryList();
        if (!patient.getMedicalHistoryList().isEmpty()) {

            TextView medHistory = findViewById(R.id.medHistoryText);
            medHistory.setText(patient.getMedicalHistoryList().get(0));
        }
    }

    /**
     * This function converts either weeks or months into months and weeks
     * example: gestational age = 6 weeks ,converts to 1 month and 1.5 weeks(roughly)
     *
     * @param patient current patient
     */
    void setupGestationalInfo(Patient patient) {
        RadioGroup radioGroup = findViewById(R.id.gestationradioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                double val = -1;
                if (i == R.id.monthradiobutton) {
                    if (patient.getGestationalAge() != null) {
                        val = patient.getGestationalAge().getAge().asMonths();
                    }
                } else {
                    if (patient.getGestationalAge() != null) {
                        val = patient.getGestationalAge().getAge().asWeeks();
                    }
                }
                if (val < 0) {
                    gestationalAge.setText("N/A");
                } else {
                    gestationalAge.setText(String.format("%.2f", val));
                }
            }
        });
        radioGroup.check(R.id.monthradiobutton);
    }

    void setupLineChart() {
        LineChart lineChart = findViewById(R.id.patientLineChart);
        CardView lineChartCard = findViewById(R.id.patientLineChartCard);
        lineChartCard.setVisibility(View.VISIBLE);

        List<Entry> sBPs = new ArrayList<>();
        List<Entry> dBPs = new ArrayList<>();
        List<Entry> bPMs = new ArrayList<>();

        //put data sets in chronological order
        int index = patientReadings.size();
        for (Reading reading : patientReadings) {
            sBPs.add(0, new Entry(index, reading.getBloodPressure().getSystolic()));
            dBPs.add(0, new Entry(index, reading.getBloodPressure().getDiastolic()));
            bPMs.add(0, new Entry(index, reading.getBloodPressure().getHeartRate()));
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

    private List<Reading> getThisPatientsReadings() {
        List<Reading> readings = readingManager.getReadingByPatientIdBlocking(currPatient.getId());
        Comparator<Reading> comparator = Reading.DescendingDateComparator.INSTANCE;
        Collections.sort(readings, comparator);
        return readings;
    }

    private void setupCreatePatientReadingButton() {
        Button createButton = findViewById(R.id.newPatientReadingButton);
        createButton.setVisibility(View.VISIBLE);
        List<Reading> readings = getThisPatientsReadings();
        boolean readingFound = false;
        Reading latestReading = null;

        if (readings.size() > 0) {
            readingFound = true;
            latestReading = readings.get(0);
        }
        //button only works if a reading exist, which it always should
        if (readingFound) {
            String readingID = latestReading.getId();
            createButton.setOnClickListener(v -> {
                Intent intent = ReadingActivity.makeIntentForNewReadingExistingPatient(PatientProfileActivity.this, readingID);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            });
        }
    }

    void setupReadingsRecyclerView() {
        patientReadings = getThisPatientsReadings();

        // use linear layout
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        readingRecyclerview.setLayoutManager(layoutManager);
        readingRecyclerview.setNestedScrollingEnabled(false);
        ReadingRecyclerViewAdapter listAdapter;

        // set adapter
        listAdapter = new ReadingRecyclerViewAdapter(patientReadings);

        listAdapter.setOnClickElementListener(new ReadingRecyclerViewAdapter.OnClickElement() {
            @Override
            public void onClick(String readingId) {
                Intent intent = ReadingActivity.makeIntentForEdit(PatientProfileActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }

            @Override
            public boolean onLongClick(String readingId) {
                askToDeleteReading(readingId);
                return true;
            }

            @Override
            public void onClickRecheckReading(String readingId) {
                Intent intent = ReadingActivity.makeIntentForRecheck(PatientProfileActivity.this, readingId);
                startActivityForResult(intent, READING_ACTIVITY_DONE);
            }
        });
        readingRecyclerview.setAdapter(listAdapter);

    }

    /**
     * shows a dialog to confirm deleting a reading
     *
     * @param readingId id of the reading to delete
     */
    private void askToDeleteReading(String readingId) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setMessage("Delete reading?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, (dialog1, whichButton) -> {
                    readingManager.deleteReadingByIdBlocking(readingId);
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