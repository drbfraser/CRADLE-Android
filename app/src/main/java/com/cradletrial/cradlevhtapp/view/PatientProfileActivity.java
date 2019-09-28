package com.cradletrial.cradlevhtapp.view;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.cradletrial.cradlevhtapp.R;
import com.cradletrial.cradlevhtapp.dagger.MyApp;
import com.cradletrial.cradlevhtapp.model.Patient.Patient;

import org.w3c.dom.Text;

public class PatientProfileActivity extends AppCompatActivity {

    public TextView patientID = (TextView) findViewById(R.id.tvEnteredID);
    public TextView patientName = (TextView) findViewById(R.id.tvEnteredPatientName);
    public TextView patientAge = (TextView) findViewById(R.id.tvEnteredAge);
    public TextView patientSex = (TextView) findViewById(R.id.tvEnteredSex);
    public TextView villageNo = (TextView) findViewById(R.id.tvEnteredVillageNumber);
    public TextView symptomsTemp = (TextView) findViewById(R.id.tvEnteredSymptoms);
    public TextView gestationalValTemp = (TextView) findViewById(R.id.tvEnteredGestationalAge);
    public TextView gestationalUnitTemp = (TextView) findViewById(R.id.tvEnteredGestationalAgeUnit);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_profile);

        // uncomment when receiving patient object from patients activity
//        populatePatientInfo((Patient) getIntent().getSerializableExtra(PatientsActivity.EXTRA_PATIENT));
    }

    void populatePatientInfo(Patient patient)
    {
        patientID.setText(patient.patientId);
        patientName.setText(patient.patientName);
        patientAge.setText(patient.ageYears);
        patientSex.setText(patient.patientSex.toString());
        villageNo.setText(patient.villageNumber);
        symptomsTemp.setText(patient.genSymptomString());
        gestationalValTemp.setText(patient.gestationalAgeValue);
        gestationalUnitTemp.setText(patient.gestationalAgeUnit.toString());
    }
}
