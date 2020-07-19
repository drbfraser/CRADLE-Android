package com.cradle.neptune.view;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cradle.neptune.R;
import com.cradle.neptune.dagger.MyApp;
import com.cradle.neptune.manager.PatientManager;
import com.cradle.neptune.model.Patient;
import com.cradle.neptune.model.Reading;
import com.cradle.neptune.model.Settings;
import com.cradle.neptune.manager.ReadingManager;
import com.cradle.neptune.viewmodel.PatientsViewAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import kotlin.Pair;

import java.util.*;

import javax.inject.Inject;

public class PatientsActivity extends AppCompatActivity {

    @Inject
    ReadingManager readingManager;
    @Inject
    PatientManager patientManager;
    @Inject
    SharedPreferences sharedPreferences;
    @Inject
    Settings settings;

    private RecyclerView patientRecyclerview;
    private PatientsViewAdapter patientsViewAdapter;

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
            getSupportActionBar().setTitle("My Patients");
        }
        setupGlobalPatientSearchButton();
    }

    private void setupGlobalPatientSearchButton() {
        ExtendedFloatingActionButton globalSearchButton = findViewById(R.id.globalPatientSearch);
        globalSearchButton.setOnClickListener(v -> {
            startActivity(new Intent(PatientsActivity.this,
                    GlobalPatientSearchActivity.class));
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_patients, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.searchPatients)
                .getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        // listening to search query text change
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // filter recycler view when query submitted
                patientsViewAdapter.getFilter().filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                // filter recycler view when text is changed
                patientsViewAdapter.getFilter().filter(query);
                return false;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.searchPatients) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupPatientRecyclerview() {

        List<Patient> patientList = patientManager.getAllPatients();
        List<Pair<Patient, Reading>> patients = new ArrayList<>();

        for (Patient patient: patientList){
            patients.add(new Pair<>(patient,readingManager.getNewestReadingByPatientId(patient.getId())));
        }

        TextView textView = findViewById(R.id.emptyView);
        if (patients.size() == 0) {
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);

        }
        patientsViewAdapter = new PatientsViewAdapter(patients, this);
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
