package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.databinding.ActivityReferralBinding
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.viewmodel.PatientReferralViewModel
import com.cradleplatform.neptune.viewmodel.ReferralFlowSaveResult
import com.cradleplatform.neptune.viewmodel.ReferralOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


@AndroidEntryPoint
open class PatientReferralActivity : AppCompatActivity() {

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private lateinit var currPatient : Patient

    private val viewModel: PatientReferralViewModel by viewModels()

    private var binding: ActivityReferralBinding? = null

    companion object {
        private const val EXTRA_PATIENT = "patient"
        private const val EXTRA_PATIENT_ID = "patient_id"

        fun makeIntentForPatient(context: Context, patient: Patient): Intent {
            val intent = Intent(context, PatientReferralActivity::class.java)
            intent.putExtra(EXTRA_PATIENT, patient)
            return intent
        }

        fun makeIntentForPatientIDReferral(
            context: Context?,
            patientId: String
        ): Intent =
            Intent(context, PatientReferralActivity::class.java).apply {
                putExtra(EXTRA_PATIENT_ID, patientId)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_referral)
        binding?.apply {
            viewModel = this@PatientReferralActivity.viewModel
            lifecycleOwner = this@PatientReferralActivity
            executePendingBindings()
        }

        populateCurrentPatient()

        setupToolBar()
        setupSendWebBtn()
        setupSendSMSBtn()
    }

    private fun populateCurrentPatient() {
        val tmpPatient = if (intent.hasExtra(EXTRA_PATIENT_ID)) {
            // Assertion here should be safe due to hasExtra check
            val patientId: String = intent.getStringExtra(EXTRA_PATIENT_ID)!!
            runBlocking { patientManager.getPatientById(patientId) }
        } else {
            intent.getSerializableExtra(EXTRA_PATIENT) as Patient?
        }

        if (tmpPatient != null) {
            currPatient = tmpPatient
        } else {
            error("PatientReferral - Patient not found")
        }
    }

    private fun setupToolBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle("Creating referral for " + currPatient.name)
    }

    private fun setupSendWebBtn() {
        val sendBtn = findViewById<Button>(R.id.send_web_button)
        sendBtn.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.saveReferral(ReferralOption.WEB, currPatient)) {
                    is ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.ErrorConstructing -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.ErrorUploadingReferral -> {
                        finish()
                    }
                }
            }
        }
    }

    private fun setupSendSMSBtn() {
        val sendBtn = findViewById<Button>(R.id.send_sms_button)
        sendBtn.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.saveReferral(ReferralOption.SMS, currPatient)) {
                    is ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.SaveSuccessful.ReferralSmsNeeded -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.ErrorConstructing -> {
                        finish()
                    }
                    is ReferralFlowSaveResult.ErrorUploadingReferral -> {
                        finish()
                    }
                }
            }
        }
    }
}