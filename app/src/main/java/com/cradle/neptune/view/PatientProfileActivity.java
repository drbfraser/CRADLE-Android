package com.cradle.neptune.view;

import android.app.AlertDialog;
import android.content.Context;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.manager.PatientManager;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.utilitiles.Util;
import com.cradle.neptune.viewmodel.ReadingRecyclerViewAdapter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

import static com.cradle.neptune.view.DashBoardActivity.READING_ACTIVITY_DONE;

@AndroidEntryPoint
public class PatientProfileActivity extends AppCompatActivity {
    private static final String EXTRA_PATIENT = "patient";
    private static final String EXTRA_PATIENT_ID = "patientId";

    TextView patientID;
    TextView patientName;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    TextView householdNo;
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
    PatientManager patientManager;
    @Inject
    SharedPreferences sharedPreferences;

    public static Intent makeIntentForPatient(Context context, Patient patient) {
        Intent intent = new Intent(context, PatientProfileActivity.class);
        intent.putExtra(EXTRA_PATIENT, patient);
        return intent;
    }

    public static Intent makeIntentForPatientId(Context context, String patientId) {
        Intent intent = new Intent(context, PatientProfileActivity.class);
        intent.putExtra(EXTRA_PATIENT_ID, patientId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_patient_profile);
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
        patientAge = findViewById(R.id.patientAge);
        patientSex = findViewById(R.id.patientSex);
        villageNo = findViewById(R.id.patientVillage);
        householdNo = findViewById(R.id.patientHouseholdNumber);
        patientZone = findViewById(R.id.patientZone);
        pregnant = findViewById(R.id.textView20);
        gestationalAge = findViewById(R.id.gestationalAge);
        pregnancyInfoLayout = findViewById(R.id.pregnancyLayout);
        readingRecyclerview = findViewById(R.id.readingRecyclerview);
    }

    boolean getLocalPatient() {
        if (getIntent().hasExtra(EXTRA_PATIENT_ID)) {
            final String patientId = getIntent().getStringExtra(EXTRA_PATIENT_ID);
            currPatient = (Patient) patientManager.getPatientByIdBlocking(patientId);
            return (currPatient != null);
        }

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
            final int ageFromDob = Patient.calculateAgeFromDateString(patient.getDob());

            final String ageDisplayString;
            if (patient.isExactDob() == null || !patient.isExactDob()) {
                ageDisplayString = getString(
                        R.string.patient_profile_age_about_n_years_old, ageFromDob);
            } else {
                ageDisplayString = getString(
                        R.string.patient_profile_age_n_years_old, ageFromDob);
            }

            patientAge.setText(ageDisplayString);
        }
        patientSex.setText(patient.getSex().toString());
        if (!Util.stringNullOrEmpty(patient.getVillageNumber())) {
            villageNo.setText(patient.getVillageNumber());
        }
        if (!Util.stringNullOrEmpty(patient.getHouseholdNumber())) {
            householdNo.setText(patient.getHouseholdNumber());
        }
        if (!Util.stringNullOrEmpty(patient.getZone())) {
            patientZone.setText(patient.getZone());
        }
        if (patient.isPregnant()) {
            pregnant.setText(R.string.yes);
            setupGestationalInfo(patient);
        } else {
            pregnant.setText(R.string.no);
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
        radioGroup.setOnCheckedChangeListener((radioGroup1, index) -> {
            double val = -1;
            if (index == R.id.monthradiobutton) {
                if (patient.getGestationalAge() != null) {
                    val = patient.getGestationalAge().getAge().asMonths();
                }
            } else {
                if (patient.getGestationalAge() != null) {
                    val = patient.getGestationalAge().getAge().asWeeks();
                }
            }
            if (val < 0) {
                gestationalAge.setText(R.string.not_available_n_slash_a);
            } else {
                gestationalAge.setText(String.format(Locale.getDefault(), "%.2f", val));
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


        LineDataSet sBPDataSet = new LineDataSet(sBPs,
                getString(R.string.activity_patient_profile_chart_systolic_label));
        LineDataSet dBPDataSet = new LineDataSet(dBPs,
                getString(R.string.activity_patient_profile_chart_diastolic_label));
        LineDataSet bPMDataSet = new LineDataSet(bPMs,
                getString(R.string.activity_patient_profile_chart_heart_rate_label));

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
        lineChart.getDescription().setText(
                getString(R.string.activity_patient_profile_line_chart_description,
                        patientReadings.size()));
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

        createButton.setOnClickListener(v -> {
            Intent intent = ReadingActivity.makeIntentForNewReadingExistingPatient(
                    PatientProfileActivity.this, currPatient.getId());
            startActivityForResult(intent, READING_ACTIVITY_DONE);
        });
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
                Intent intent = ReadingActivity.makeIntentForEditReading(PatientProfileActivity.this, readingId);
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
                .setMessage(R.string.activity_patient_profile_delete_reading_dialog_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(
                        R.string.activity_patient_profile_delete_reading_dialog_delete_button,
                        (dialog1, whichButton) -> {
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