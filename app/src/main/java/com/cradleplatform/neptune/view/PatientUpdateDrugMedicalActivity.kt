package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.viewmodel.EditPatientViewModel.SaveResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PatientUpdateDrugMedicalActivity : AppCompatActivity() {
    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var isNetworkAvailable: NetworkAvailableLiveData

    companion object {
        private const val EXTRA_PATIENT = "patient"

        fun makeIntent(context: Context, isDrugRecord: Boolean, currPatient: Patient): Intent {
            val intent = Intent(context, PatientUpdateDrugMedicalActivity::class.java)
            intent.putExtra("isDrugRecord", isDrugRecord)
            intent.putExtra(EXTRA_PATIENT, currPatient)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_patient_update_drug_medical)

        val patient: Patient = intent.getSerializableExtra("patient") as Patient
        val isDrugRecord = intent.getBooleanExtra("isDrugRecord", true)

        setupInternetConnectionCheck()
        setupPageHeader(patient, isDrugRecord)
        setupSaveButton(patient, isDrugRecord)
        setupToolBar(isDrugRecord)
    }

    private fun setupInternetConnectionCheck() {
        val noInternetText = findViewById<TextView>(R.id.internetAvailabilityTextView)
        isNetworkAvailable = NetworkAvailableLiveData(this).apply {
            observe(this@PatientUpdateDrugMedicalActivity) { netAvailable ->
                netAvailable ?: return@observe
                noInternetText.isVisible = !netAvailable
            }
        }
    }

    private fun setupPageHeader(patient: Patient, isDrugRecord: Boolean) {
        val input = findViewById<TextInputEditText>(R.id.recordText)
        if (isDrugRecord && patient.drugHistory.isNotEmpty()) {
            input.setText(patient.drugHistory)
        } else if (!isDrugRecord && patient.medicalHistory.isNotEmpty()) {
            input.setText(patient.medicalHistory)
        }
        findViewById<TextView>(R.id.patientName).text = patient.name
    }

    private fun setupSaveButton(patient: Patient, isDrugRecord: Boolean) {
        val input = findViewById<TextInputEditText>(R.id.recordText)
        findViewById<Button>(R.id.historySaveButton).setOnClickListener {
            val record: String = input.text.toString()
            if (record.isEmpty()) {
                input.error = "Cannot leave blank"
            } else {
                input.isEnabled = false

                if (isDrugRecord) patient.drugHistory = record
                else patient.medicalHistory = record

                lifecycleScope.launch {
                    when (saveAndUploadPatient(patient, isDrugRecord)) {
                        is SaveResult.SavedAndUploaded -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.edit_online_success_msg),
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        is SaveResult.SavedOffline -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.edit_offline_success_msg),
                                Toast.LENGTH_LONG
                            ).show()
                            finish()
                        }
                        is SaveResult.ServerReject -> {
                            MaterialAlertDialogBuilder(it.context)
                                .setTitle(R.string.server_error)
                                .setMessage(R.string.data_invalid_please_sync)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                            input.isEnabled = true
                        }
                        else -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.edit_fail_msg),
                                Toast.LENGTH_LONG
                            ).show()
                            input.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun setupToolBar(isDrugRecord: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(
            if (isDrugRecord) R.string.patient_update_drug_history
            else R.string.patient_update_medical_history
        )
    }

    private suspend fun saveAndUploadPatient(patient: Patient, isDrugRecord: Boolean): SaveResult {
        return if (isNetworkAvailable.value == true) {
            when (patientManager.updatePatientMedicalRecord(patient, isDrugRecord)) {
                is NetworkResult.Success -> {
                    SaveResult.SavedAndUploaded
                }
                else -> {
                    SaveResult.ServerReject
                }
            }
        } else {
            saveRecordOffline(patient, isDrugRecord)
        }
    }

    private suspend fun saveRecordOffline(patient: Patient, isDrugRecord: Boolean): SaveResult {
        if (isDrugRecord) {
            patient.drugLastEdited = UnixTimestamp.now.toLong()
        } else {
            patient.medicalLastEdited = UnixTimestamp.now.toLong()
        }
        patientManager.add(patient)
        return SaveResult.SavedOffline
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

}
