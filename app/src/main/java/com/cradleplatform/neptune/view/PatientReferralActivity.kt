package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.util.Base64
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
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.SmsReferral
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import com.cradleplatform.neptune.viewmodel.PatientReferralViewModel
import com.cradleplatform.neptune.viewmodel.ReferralFlowSaveResult
import com.cradleplatform.neptune.viewmodel.ReferralOption
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
open class PatientReferralActivity : AppCompatActivity() {

    @Inject
    lateinit var patientManager: PatientManager

    private lateinit var currPatient: Patient

    private val viewModel: PatientReferralViewModel by viewModels()

    private var binding: ActivityReferralBinding? = null

    private val dataBindingComponent: DataBindingComponent = FragmentDataBindingComponent()

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
        binding = DataBindingUtil.setContentView(this, R.layout.activity_referral, dataBindingComponent)
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
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
                            launchSmsIntentAndFinish(smsSendResult.patientInfoForReferral)
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

    private fun encodeBase64(json: String): String {
        return Base64.encodeToString(json.toByteArray(), Base64.DEFAULT)
    }

    private fun launchSmsIntentAndFinish(
        patientAndReferrals: PatientAndReferrals
    ) {
        this.run {
            val smsIntent = makeSmsIntent(patientAndReferrals)
            startActivity(smsIntent)
            finish()
        }
    }

    private fun makeSmsIntent(
        patientAndReferrals: PatientAndReferrals
    ): Intent {
        val selectedHealthFacility = viewModel.getActiveHealthFacility()
        val genReferralId = UUID.randomUUID().toString()
        var referralMsg: String = "**referral message start: $genReferralId**\n"
        val json = JacksonMapper.createWriter<SmsReferral>().writeValueAsString(
            SmsReferral(
                referralId = genReferralId,
                patient = patientAndReferrals
            )
        )
        val phoneNumber = selectedHealthFacility.phoneNumber
        val uri = Uri.parse("smsto:$phoneNumber")

        val encodedMsg = encodeBase64(json)
        referralMsg += "$encodedMsg**end of referral message**"

        return Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("address", phoneNumber)
            putExtra("sms_body", referralMsg)

            Telephony.Sms.getDefaultSmsPackage(applicationContext)?.let {
                setPackage(it)
            }
        }
    }
}
