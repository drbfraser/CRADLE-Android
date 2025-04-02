package com.cradleplatform.neptune.activities.patients

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ActivityReferralBinding
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.http_sms_service.sms.ui.SmsTransmissionDialogFragment
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReferralUploadManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.makeErrorSnackbar
import com.cradleplatform.neptune.viewmodel.patients.PatientReferralViewModel
import com.cradleplatform.neptune.viewmodel.patients.ReferralFlowSaveResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
open class PatientReferralActivity : AppCompatActivity() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var referralUploadManager: ReferralUploadManager

    @Inject
    lateinit var smsStateReporter: SmsStateReporter

    private lateinit var currPatient: Patient

    private val viewModel: PatientReferralViewModel by viewModels()

    private var binding: ActivityReferralBinding? = null

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    private var triedSendingViaSms = false

    companion object {
        private const val EXTRA_PATIENT = "patient"
        private const val EXTRA_PATIENT_ID = "patient_id"

        private const val TAG = "PatientReferralActivity"

        fun makeIntentForPatient(context: Context, patient: Patient): Intent {
            val intent = Intent(context, PatientReferralActivity::class.java)
            intent.putExtra(EXTRA_PATIENT, patient)
            return intent
        }
    }

    override fun onResume() {
        Log.d(TAG, "$TAG::onResume()")
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding =
            DataBindingUtil.setContentView(this, R.layout.activity_referral, dataBindingComponent)
        binding?.apply {
            viewModel = this@PatientReferralActivity.viewModel
            lifecycleOwner = this@PatientReferralActivity
            executePendingBindings()
        }
        populateCurrentPatient()

        setupToolBar()
        setupSendButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onStop() {
        Log.d(TAG, "$TAG::onStop()")
        if (triedSendingViaSms) {
            CustomToast.shortToast(
                applicationContext,
                applicationContext.getString(R.string.save_locally_toast)
            )
        }
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "$TAG::onDestroy()")
        super.onDestroy()
    }

    private fun showReferralUploadError() {
        makeErrorSnackbar(
            findViewById(android.R.id.content),
            "Error: Referral Upload Failed"
        ).show()
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

    private fun setupSendButtons() {
        fun setNetworkErrorResult() = setResult(RESULT_OK, Intent().putExtra("NETWORK_ERROR", true))

        val sendViaHTTP = findViewById<Button>(R.id.send_web_button)
        sendViaHTTP.setOnClickListener {
            val referral = viewModel.buildReferral(currPatient)
            lifecycleScope.launch {
                val result =
                    referralUploadManager.uploadReferral(referral, currPatient, Protocol.HTTP)
                when (result) {
                    is ReferralFlowSaveResult.SaveSuccessful -> {
                        Log.i(TAG, "HTTP Referral upload succeeded!")
                        finish()
                    }

                    is ReferralFlowSaveResult.NetworkError -> {
                        Log.e(TAG, "HTTP Referral upload failed due to network error!")
                        setNetworkErrorResult()
                        finish()
                    }

                    else -> {
                        Log.e(TAG, "HTTP Referral upload failed!")
                        showReferralUploadError()
                    }
                }
            }
        }

        val sendViaSMS = findViewById<Button>(R.id.send_sms_button)
        sendViaSMS.setOnClickListener {
            triedSendingViaSms = true
            val referral = viewModel.buildReferral(currPatient)
            openSmsTransmissionDialog()

            lifecycleScope.launch {
                Toast.makeText(
                    applicationContext,
                    applicationContext.getString(R.string.sms_sender_send),
                    Toast.LENGTH_SHORT
                ).show()

                val result =
                    referralUploadManager.uploadReferral(referral, currPatient, Protocol.SMS)
                when (result) {
                    is ReferralFlowSaveResult.SaveSuccessful -> {
                        Log.i(TAG, "SMS Referral upload succeeded!")
                    }

                    is ReferralFlowSaveResult.NetworkError -> {
                        Log.e(TAG, "HTTP Referral upload failed due to network error!")
                        setNetworkErrorResult()
                    }

                    else -> {
                        Log.e(TAG, "SMS Referral upload failed!")
                        showReferralUploadError()
                    }
                }
                finish()
            }
        }
    }

    private fun openSmsTransmissionDialog() {
        val smsTransmissionDialogFragment = SmsTransmissionDialogFragment()
        smsTransmissionDialogFragment.show(
            supportFragmentManager,
            "$TAG::${SmsTransmissionDialogFragment.TAG}"
        )
    }
}
