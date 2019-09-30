package com.cradletrial.cradlevhtapp.view;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;


public class PatientProfileActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);

        Patient mPatient = (Patient) getIntent().getSerializableExtra("key");
        populatePatientInfo(mPatient);

    }

    void populatePatientInfo(Patient patient)
    {
        TextView patientID = (TextView) findViewById(R.id.tvEnteredID);
        TextView patientName = (TextView) findViewById(R.id.tvEnteredPatientName);
        TextView patientAge = (TextView) findViewById(R.id.tvEnteredAge);
        TextView patientSex = (TextView) findViewById(R.id.tvEnteredSex);
        TextView villageNo = (TextView) findViewById(R.id.tvEnteredVillageNumber);
        TextView symptomsTemp = (TextView) findViewById(R.id.tvEnteredSymptoms);
        TextView gestationalValTemp = (TextView) findViewById(R.id.tvEnteredGestationalAge);
        TextView gestationalUnitTemp = (TextView) findViewById(R.id.tvEnteredGestationalAgeUnit);

        patientID.setText(patient.patientId);
        patientName.setText(patient.patientName);
        patientAge.setText(patient.ageYears.toString());
        patientSex.setText(patient.patientSex.toString());
        villageNo.setText(patient.villageNumber);
        symptomsTemp.setText(patient.genSymptomString());
        gestationalValTemp.setText(patient.gestationalAgeValue);

        if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_MONTHS")) {
            gestationalUnitTemp.setText("Months");
        } else if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_WEEKS")){
            gestationalUnitTemp.setText("Weeks");
        } else if (patient.gestationalAgeUnit.toString().equals("GESTATIONAL_AGE_UNITS_NONE")){
            gestationalUnitTemp.setText("None");
        }
    }
}