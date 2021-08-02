package com.cradleplatform.neptune.view

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import com.cradleplatform.neptune.view.DashBoardActivity.Companion.UPDATE_ACTIVITY_DONE

@AndroidEntryPoint
class PatientUpdateDrugMedicalActivity : AppCompatActivity() {
    @Inject
    lateinit var patientManager: PatientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_update_drug_medical)

        var patient: Patient = intent.getSerializableExtra("patient") as Patient
        val isDrugRecord = intent.getBooleanExtra("isDrugRecord", true )

        val input = findViewById<TextInputEditText>(R.id.recordText)
        if(isDrugRecord && patient.drugHistory.isNotEmpty()){
            input.setText(patient.drugHistory)
        }
        else if (!isDrugRecord && patient.medicalHistory.isNotEmpty()){
            input.setText(patient.medicalHistory)
        }

        findViewById<TextView>(R.id.patientName).text = patient.name

        findViewById<Button>(R.id.historySaveButton).setOnClickListener {
            val record: String = input.text.toString()
            if(record.isEmpty()){
                input.error = "Cannot leave blank"
            }
            else{
                if(isDrugRecord) patient.drugHistory = record
                else patient.medicalHistory = record
                runBlocking { patientManager.updatePatientMedicalRecord(patient, isDrugRecord) }
                val resultIntent = Intent()
                resultIntent.putExtra("patientId", patient.id)
                setResult(UPDATE_ACTIVITY_DONE, resultIntent);
                finish()
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(
            if (isDrugRecord) R.string.patient_update_drug_history
            else R.string.patient_update_medical_history)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

