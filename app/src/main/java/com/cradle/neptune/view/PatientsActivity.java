package com.cradle.neptune.view;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
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
import com.cradle.neptune.service.DatabaseService;
import com.cradle.neptune.viewmodel.PatientsViewAdapter;

import kotlin.Pair;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.*;

import javax.inject.Inject;

public class PatientsActivity extends AppCompatActivity {

    // Data Model
    @Inject
    ReadingManager readingManager;

    @Inject
    DatabaseService databaseService;

    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    Settings settings;

    private RecyclerView patientRecyclerview;
    private PatientsViewAdapter patientsViewAdapter;
    private SearchView searchView;


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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_patients, menu);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView = (SearchView) menu.findItem(R.id.searchPatients)
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

        HashMap<String, Pair<Patient, Reading>> patientHashMap = new HashMap<>();

//        List<Reading> allReadings = readingManager.getReadings(this);
        List<Pair<Patient, Reading>> allReadings = databaseService.getAllReadingsBlocking();
        Collections.sort(allReadings, Comparator.comparing(o -> o.getSecond().getDateTimeTaken()));


        for (Pair<Patient, Reading> pair : allReadings) {
            if (!patientHashMap.containsKey(pair.getSecond().getPatientId())) {
                patientHashMap.put(pair.getSecond().getPatientId(), pair);
            }
        }
        List<Pair<Patient, Reading>> patients = new ArrayList<>(patientHashMap.values());
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
