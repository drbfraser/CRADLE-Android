package com.cradleplatform.neptune.view

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PatientUpdateDrugMedicalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_update_drug_medical)

        if(intent.hasExtra("record")){
            val record = intent.getStringExtra("record")!!
            if(record.isNotEmpty()){
                findViewById<TextInputEditText>(R.id.recordText).setText(record)
            }
        }

        if(intent.hasExtra("patientName")){
            findViewById<TextView>(R.id.patientName).text = intent.getStringExtra("patientName")
        }

        findViewById<Button>(R.id.historySaveButton).setOnClickListener {

        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if(intent.hasExtra("isDrugRecord")){
            supportActionBar?.setTitle(
                if (intent.getBooleanExtra("isDrugRecord", true ))
                    R.string.patient_update_drug_history
                else R.string.patient_update_medical_history)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

