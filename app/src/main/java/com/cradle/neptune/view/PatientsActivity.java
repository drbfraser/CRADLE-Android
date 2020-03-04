package com.cradle.neptune.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.ReadingManager;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.viewmodel.PatientsViewAdapter;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

public class PatientsActivity extends AppCompatActivity {

    // Data Model
    @Inject
    ReadingManager readingManager;

    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    Settings settings;

    private RecyclerView patientRecyclerview;
    private PatientsViewAdapter patientsViewAdapter;


    // set who we are for tab code
    public PatientsActivity() {
        //super(R.id.nav_patients);
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, PatientsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // inject:
        ((MyApp) getApplication()).getAppComponent().inject(this);

        // setup UI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patients);
        Toolbar toolbar = findViewById(R.id.toolbar);
        patientRecyclerview = findViewById(R.id.patientListRecyclerview);
        setupPatientRecyclerview();
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupPatientRecyclerview() {

        HashMap<String, Pair<Patient, Reading>> patientHashMap = new HashMap<>();

        List<Reading> allReadings = readingManager.getReadings(this);

        Collections.sort(allReadings, new Reading.ComparatorByDateReverse());


        for (Reading reading : allReadings) {
            if (!patientHashMap.containsKey(reading.patient.patientId)) {
                patientHashMap.put(reading.patient.patientId, new Pair<>(reading.patient, reading));
            }
        }
        List<Pair<Patient, Reading>> patients = new ArrayList<>(patientHashMap.values());
        TextView textView = findViewById(R.id.emptyView);
        if (patients.size() == 0) {
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);

        }
        PatientsViewAdapter patientsViewAdapter = new PatientsViewAdapter(patients, this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        patientRecyclerview.setAdapter(patientsViewAdapter);
        patientRecyclerview.setLayoutManager(layoutManager);
        patientRecyclerview.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        patientsViewAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupPatientRecyclerview();
    }

}
