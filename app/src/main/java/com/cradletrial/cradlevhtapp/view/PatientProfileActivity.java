package com.cradletrial.cradlevhtapp.view;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;


public class PatientProfileActivity extends AppCompatActivity {

    TextView patientID;
    TextView patientName;
    TextView patientAge;
    TextView patientSex;
    TextView villageNo;
    TextView symptomsTemp;
    TextView gestationalValTemp;
    TextView gestationalUnitTemp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);

        patientID = (TextView) findViewById(R.id.patientId);
        patientName = (TextView) findViewById(R.id.patientName);
        patientAge = (TextView) findViewById(R.id.patientAge);
        patientSex = (TextView) findViewById(R.id.patientSex);
        villageNo = (TextView) findViewById(R.id.patientVillage);

        Patient mPatient = (Patient) getIntent().getSerializableExtra("key");
        populatePatientInfo(mPatient);

        if(getSupportActionBar()!=null){
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.patient_summary);
        }

    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    void populatePatientInfo(Patient patient)
    {
        patientID.setText(patient.patientId);
        patientName.setText(patient.patientName);
        patientAge.setText(patient.ageYears.toString());
        patientSex.setText(patient.patientSex.toString());
        villageNo.setText(patient.villageNumber);
      //  symptomsTemp.setText(patient.genSymptomString());
        //gestationalValTemp.setText(patient.gestationalAgeValue);
//
//        if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_MONTHS")) {
//            gestationalUnitTemp.setText("Months");
//        } else if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_WEEKS")){
//            gestationalUnitTemp.setText("Weeks");
//        } else if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_NONE")){
//            gestationalUnitTemp.setText("None");
//        }
    }
}