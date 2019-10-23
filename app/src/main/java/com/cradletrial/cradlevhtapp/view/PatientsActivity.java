package com.cradletrial.cradlevhtapp.view;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.Button;
import android.widget.Toast;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;
import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.ReadingManager;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.cradletrial.cradlevhtapp.viewmodel.PatientsViewAdapter;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

public class PatientsActivity extends TabActivityBase {

    // Data Model
    @Inject
    ReadingManager readingManager;

    @Inject
    SharedPreferences sharedPreferences;

    @Inject
    Settings settings;

    private RecyclerView patientRecyclerview;
    private PatientsViewAdapter patientsViewAdapter;


    public static Intent makeIntent(Context context) {
        return new Intent(context, PatientsActivity.class);
    }

    // set who we are for tab code
    public PatientsActivity() {
        super(R.id.nav_patients);
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
        if(getSupportActionBar()!=null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // bottom bar nav in base class
        //setupBottomBarNavigation();

        // buttons
        setupAddSampleDataButton();
        setupClearDBButton();
        setupPretendToUnUploadToServer();
        setupSettingsAddFake();
        setupSettingsClear();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupPatientRecyclerview() {
        List<Patient> patients = new ArrayList<>();
        for(int i =0;i<25;i++){
            Patient patient = new Patient(i*1000000+"","P"+i,i+20,
                    Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS," ??",
                    30+i+"", Patient.PATIENTSEX.FEMALE,"ZONE "+i,"tank "+i,"HN "+i,false);
            patients.add(patient);
        }
        PatientsViewAdapter patientsViewAdapter = new PatientsViewAdapter(patients,this);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        patientRecyclerview.setAdapter(patientsViewAdapter);
        patientRecyclerview.setLayoutManager(layoutManager);
        patientRecyclerview.addItemDecoration(new DividerItemDecoration(this,DividerItemDecoration.VERTICAL));
        patientsViewAdapter.notifyDataSetChanged();
    }


    private void setupAddSampleDataButton() {
        Button btn = findViewById(R.id.btnAddSampleData);
        btn.setOnClickListener(view -> {
            Toast.makeText(this, "Adding sample data...", Toast.LENGTH_LONG).show();

            List<Reading> tmpList = new ArrayList<>();
            long timeDelta = 0;
            for (int i = 0; i < 30; i++ ) {
                int makeNeg = (i % 2 == 0) ? 1 : -1;
                Reading r = new Reading();
                r.patient.patientName = "P" + (char)('A' + i);
                r.patient.patientId = String.valueOf(48300027400L + i + ((i * new Random().nextLong() % 10000000L)* 1000));
                r.patient.ageYears = 20 + i;
                r.dateTimeTaken = ZonedDateTime.now().minus(timeDelta, ChronoUnit.MINUTES);
                r.bpSystolic = 120 + (i * 15) * makeNeg;
                r.bpDiastolic = 80 + (i * 5) * makeNeg;
                r.heartRateBPM = 100 + (i * 10) * makeNeg;
                r.gpsLocationOfReading = "1.3733° N, 32.2903° E";
                r.setFlaggedForFollowup( i % 3 == 1);
                if (i % 2 == 1) {
                    r.setReferredToHealthCentre(
                            "Bibi Bibi health centre #5",
                            r.dateTimeTaken.plus(5, ChronoUnit.MINUTES)
                    );
                }

                if (i >= 10) {
                    r.dateUploadedToServer = r.dateTimeTaken.plusHours(3);
                }

                if (i < 20) {
                    r.dateRecheckVitalsNeeded = ZonedDateTime.now().plusSeconds( (i-1) * 60);
                }

                if (i % 3 == 0) {
                    r.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_WEEKS;
                    r.patient.gestationalAgeValue = "" + (i % 45);
                }
                if (i % 3 == 1) {
                    r.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_MONTHS;
                    r.patient.gestationalAgeValue = "" + (i % 11);
                }
                if (i % 3 == 2) {
                    r.patient.gestationalAgeUnit = Reading.GestationalAgeUnit.GESTATIONAL_AGE_UNITS_NONE;
                }


                timeDelta = (timeDelta) * 2 + 1;
                if (timeDelta > 1_000_000_000) {
                    timeDelta = 7;
                }

                tmpList.add(r);
            }
            for (int i = tmpList.size() - 1; i >= 0; i--) {
                Reading r = tmpList.get(i);
                readingManager.addNewReading(this, r);
            }
        });

    }

    private void setupClearDBButton() {
        Button btn = findViewById(R.id.btnClearDatabase);
        btn.setOnClickListener(view -> {
            readingManager.deleteAllData(this);
            Toast.makeText(this, "Cleared all data", Toast.LENGTH_LONG).show();

        });

    }

    private void setupPretendToUnUploadToServer() {
        Button btn = findViewById(R.id.btnDatabaseTest);
        btn.setOnClickListener(view -> {
            int count = 0;
            List<Reading> readings = readingManager.getReadings(this);
            for (Reading r : readings) {
                if (r.isUploaded()) {
                    count++;
                }
//                r.dateUploadedToServer = ZonedDateTime.now();
                r.dateUploadedToServer = null;
                readingManager.updateReading(this, r);
            }
            Toast.makeText(
                    PatientsActivity.this,
                    "Pretended to un-upload " + count + " readings.",
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void setupSettingsAddFake() {
        Button btn = findViewById(R.id.btnSettingsAddFake);
        btn.setOnClickListener(view -> {
            // Write fake settings
            sharedPreferences.edit().clear().commit();
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(Settings.PREF_KEY_VHT_NAME, "Clark the Shark");
            edit.putInt(Settings.PREF_KEY_NUM_HEALTH_CENTRES, 2);
            edit.putString(Settings.PREF_KEY_HEALTH_CENTRE_NAME_+"0", "Bidibidi Health Centre #1 (Zone 5)");
            edit.putString(Settings.PREF_KEY_HEALTH_CENTRE_CELL_+"0", "+5 235 2352-2352");
            edit.putString(Settings.PREF_KEY_HEALTH_CENTRE_NAME_+"1", "Bidibidi Regional Hospital");
            edit.putString(Settings.PREF_KEY_HEALTH_CENTRE_CELL_+"1", "+1 325 2352 3523 52");

            edit.putString(Settings.PREF_KEY_SERVER_URL, Settings.DEFAULT_SERVER_URL);
            String LINEFEED = "\r\n";
            edit.putString(Settings.PREF_KEY_RSAPUBKEY, Settings.DEFAULT_SERVER_RSA);
            edit.putString(Settings.PREF_KEY_SERVER_USERNAME, Settings.DEFAULT_SERVER_USERNAME);
            edit.putString(Settings.PREF_KEY_SERVER_PASSWORD, Settings.DEFAULT_SERVER_USERPASSWORD);

            edit.commit();
            settings.loadFromSharedPrefs();



            Toast.makeText(this, "Add fake settings (testing)", Toast.LENGTH_LONG).show();
        });

    }

    private void setupSettingsClear() {
        Button btn = findViewById(R.id.btnSettingsClear);
        btn.setOnClickListener(view -> {
            sharedPreferences.edit().clear().commit();

            // settings: load defaults if not previously set
            android.support.v7.preference.PreferenceManager.setDefaultValues(
                    this,R.xml.preferences, true);

            settings.loadFromSharedPrefs();
            Toast.makeText(this, "Cleared all settings", Toast.LENGTH_LONG).show();
        });
    }

}
