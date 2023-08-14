package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingComponent
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.binding.FragmentDataBindingComponent
import com.cradleplatform.neptune.databinding.ActivityReferralBinding
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.http_sms_service.sms.SMSReceiver
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.viewmodel.PatientReferralViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import android.content.IntentFilter
import com.cradleplatform.neptune.manager.LoginManager

@AndroidEntryPoint
open class PatientReferralActivity : AppCompatActivity() {
    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var smsSender: SMSSender

    private lateinit var currPatient: Patient

    private val viewModel: PatientReferralViewModel by viewModels()

    private var binding: ActivityReferralBinding? = null

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

    private lateinit var smsReceiver: SMSReceiver

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
        setupSMSReceiver()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_referral, dataBindingComponent)
        binding?.apply {
            viewModel = this@PatientReferralActivity.viewModel
            lifecycleOwner = this@PatientReferralActivity
            executePendingBindings()
        }

        smsSender = SMSSender(sharedPreferences, this)

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
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver)
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
        val sendViaHTTP = findViewById<Button>(R.id.send_web_button)
        sendViaHTTP.setOnClickListener {
            lifecycleScope.launch {
                //Passing context to viewModel might not be the best idea,
                // however, it is required as of now to resolve the strings
                val unusedResult = viewModel.saveReferral("HTTP", currPatient, applicationContext)
                // do something with the result (check success or failure) / do it elsewhere?
            }
        }

        val sendViaSMS = findViewById<Button>(R.id.send_sms_button)
        sendViaSMS.setOnClickListener {
            lifecycleScope.launch {
                //Passing context to viewModel might not be the best idea,
                // however, it is required as of now to resolve the strings
                val unusedResult = viewModel.saveReferral("SMS", currPatient, applicationContext)
            }
        }
    }

    private fun setupSMSReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        intentFilter.priority = Int.MAX_VALUE

        val phoneNumber = sharedPreferences.getString(LoginManager.CURRENT_PHONE_NUMBER, null)
            ?: error("invalid phone number")
        smsReceiver = SMSReceiver(smsSender, phoneNumber)
        registerReceiver(smsReceiver, intentFilter)
    }

/* Moved to View Model
    private fun sendSms(
        patientAndReferrals: PatientAndReferrals
    ) {
        val json = JacksonMapper.createWriter<SmsReferral>().writeValueAsString(
            SmsReferral(
                patient = patientAndReferrals
            )
        )

        val encodedMsg = encodeMsg(
            json,
            RelayAction.REFERRAL,
            getSecretKeyFromString(getString(R.string.aes_secret_key))
        )
        val msgInPackets = listToString(formatSMS(encodedMsg))

        sharedPreferences.edit(commit = true) {
            putString(getString(R.string.sms_relay_list_key), msgInPackets)
        }
        smsSender.sendSmsMessage(false)

        Toast.makeText(
            this, getString(R.string.sms_sender_send),
            Toast.LENGTH_LONG
        ).show()
    }

 */

    /*
    private fun setupSendWebBtn() {
        val sendBtn = findViewById<Button>(R.id.send_web_button)
        sendBtn.setOnClickListener {
            lifecycleScope.launch {
                when (viewModel.saveReferral(ReferralOption.HTML, currPatient)) {
                    is ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.patient_referral_activity_save_success_nosms),
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    is ReferralFlowSaveResult.ErrorConstructing -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.patient_referral_activity_save_error_construction),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ReferralFlowSaveResult.ErrorUploadingReferral -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.patient_referral_activity_save_error_upload),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            it.context,
                            getString(R.string.patient_referral_activity_save_error_unknown),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupSendSMSBtn() {
        val sendBtn = findViewById<Button>(R.id.send_sms_button)
        sendBtn.setOnClickListener {
            if (viewModel.isSelectedHealthFacilityValid() &&
                viewModel.isSending.value != true
            ) {
                viewModel.isSending.value = true

                lifecycleScope.launch {
                    when (val smsSendResult = viewModel.saveReferral(ReferralOption.SMS, currPatient)) {
                        is ReferralFlowSaveResult.SaveSuccessful.ReferralSmsNeeded -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.patient_referral_activity_save_success_smsneeded),
                                Toast.LENGTH_LONG
                            ).show()
                            sendSms(smsSendResult.patientInfoForReferral)
                        }
                        is ReferralFlowSaveResult.ErrorConstructing -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.patient_referral_activity_save_error_construction),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is ReferralFlowSaveResult.ErrorUploadingReferral -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.patient_referral_activity_save_error_upload),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {
                            Toast.makeText(
                                it.context,
                                getString(R.string.patient_referral_activity_save_error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
    */
}
