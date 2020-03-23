package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        currPatient = (Patient) getIntent().getSerializableExtra("patient");
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
        if (!Util.stringNullOrEmpty(patient.dob)) {
            patientDOB.setText(patient.dob);
        }
        if (!Util.stringNullOrEmpty(patient.age + "")) {
            patientAge.setText(patient.age + "");
        }
        patientSex.setText(patient.patientSex.toString());
        if (!Util.stringNullOrEmpty(patient.villageNumber)) {
            villageNo.setText(patient.villageNumber);
        }
        if (!Util.stringNullOrEmpty(patient.zone)) {
            patientZone.setText(patient.zone);
        }
        if (patient.isPregnant) {
            pregnant.setText("Yes");
            setupGestationalInfo(patient);
        } else {
            pregnant.setText("No");
            pregnancyInfoLayout.setVisibility(View.GONE);
        }

        if (patient.drugHistoryList != null && !patient.drugHistoryList.isEmpty()) {
            TextView drugHistroy = findViewById(R.id.drugHistroyTxt);
            drugHistroy.setText(patient.drugHistoryList.get(0));

        }
        if (patient.medicalHistoryList != null && !patient.medicalHistoryList.isEmpty()) {

            TextView medHistory = findViewById(R.id.medHistoryText);
            medHistory.setText(patient.medicalHistoryList.get(0));
        }
    }

    /**
     * This function converts either weeks or months into months and weeks
     * example: gestational age = 6 weeks ,converts to 1 month and 1.5 weeks(roughly)
     *
     * @param patient current patient
     */
    private void setupGestationalInfo(Patient patient) {
        RadioGroup radioGroup = findViewById(R.id.gestationradioGroup);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                double val = -1;
                if (i == R.id.monthradiobutton) {
                    val = convertGestationAgeToMonth(patient);
                    if (val < 0) {
                        gestationalAge.setText("N/A");
                    } else {
                        gestationalAge.setText(val + "");
                    }
                } else {
                    val = convertGestationAgeToWeek(patient);
                }
                if (val < 0) {
                    gestationalAge.setText("N/A");
                } else {
                    gestationalAge.setText(val + "");
                }
            }
        });
        radioGroup.check(R.id.monthradiobutton);
    }

    private double convertGestationAgeToWeek(Patient patient) {
        double age = 0;
        if (patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS && patient.isPregnant) {
            try {
                age = Double.parseDouble(patient.gestationalAgeValue);
            } catch (NumberFormatException e) {
                age = -1;
                e.printStackTrace();
            }
            double week = age * WEEKS_IN_MONTH;
            double weekRounded = Math.round(week * 100D) / 100D;
            return weekRounded;
        } else if (patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS && patient.isPregnant) {
            try {
                age = Double.parseDouble(patient.gestationalAgeValue);
            } catch (NumberFormatException e) {
                age = -1;
                e.printStackTrace();
            }
        }
        return age;
    }

    private double convertGestationAgeToMonth(Patient patient) {
        double age = 0;
        if (patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS && patient.isPregnant) {
            try {
                age = Double.parseDouble(patient.gestationalAgeValue);
            } catch (NumberFormatException e) {
                age = -1;
                e.printStackTrace();
            }
            double months = age / WEEKS_IN_MONTH;
            double monthRounded = Math.round(months * 100D) / 100D;
            return monthRounded;
        } else if (patient.gestationalAgeUnit == Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS && patient.isPregnant) {
            try {
                age = Double.parseDouble(patient.gestationalAgeValue);
            } catch (NumberFormatException e) {
                age = -1;
                e.printStackTrace();
            }
        }
        return age;
    }

    private void getPatientReadings() {
        patientReadings = readingManager.getReadingByPatientID(this,currPatient.patientId);
        Collections.sort(patientReadings, new Reading.ComparatorByDateReverse());
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
        if (readingFound) {
            String readingID = latestReading.readingId;
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

    private void askToDeleteReading(String readingId) {
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