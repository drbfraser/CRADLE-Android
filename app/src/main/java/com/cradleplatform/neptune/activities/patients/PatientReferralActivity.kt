package com.cradleplatform.neptune.activities.patients

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ActivityReferralBinding
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.http_sms_service.sms.SmsStateReporter
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.viewmodel.patients.PatientReferralViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
open class PatientReferralActivity : AppCompatActivity() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var patientManager: PatientManager

    @Inject
    lateinit var smsSender: SMSSender

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

        fun makeIntentForPatient(context: Context, patient: Patient): Intent {
            val intent = Intent(context, PatientReferralActivity::class.java)
            intent.putExtra(EXTRA_PATIENT, patient)
            return intent
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_referral, dataBindingComponent)
        binding?.apply {
            viewModel = this@PatientReferralActivity.viewModel
            lifecycleOwner = this@PatientReferralActivity
            executePendingBindings()
        }

        smsSender.setActivityContext(this)

        populateCurrentPatient()

        setupToolBar()
        //setupSendWebBtn()
        //setupSendSMSBtn()
        setupSendButtons()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onStop() {
        if (triedSendingViaSms) {
            CustomToast.shortToast(
                applicationContext,
                applicationContext.getString(R.string.save_locally_toast)
            )
        }
        super.onStop()
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
        // TODO: In both of the following buttons, the result is not being used.
        // Furthermore, the saveReferralFunction is not even returning back to this block.
        // Which is why the toast never appears, and we cannot do anything with the output.
        // We need to return and verify the output
        val sendViaHTTP = findViewById<Button>(R.id.send_web_button)
        sendViaHTTP.setOnClickListener {
            viewModel.saveReferral(Protocol.HTTP, currPatient)
            CustomToast.shortToast(
                applicationContext,
                applicationContext.getString(R.string.referral_submitted)
            )
            finish()
            // do something with the result (check success or failure) / do it elsewhere?
        }

        val sendViaSMS = findViewById<Button>(R.id.send_sms_button)
        sendViaSMS.setOnClickListener {
                triedSendingViaSms = true
                viewModel.saveReferral(Protocol.SMS, currPatient)

                Toast.makeText(
                    applicationContext,
                    applicationContext.getString(R.string.sms_sender_send),
                    Toast.LENGTH_SHORT
                ).show()

                CustomToast.shortToast(
                    applicationContext,
                    applicationContext.getString(R.string.sms_sender_send)
                )
            }
    }

}
