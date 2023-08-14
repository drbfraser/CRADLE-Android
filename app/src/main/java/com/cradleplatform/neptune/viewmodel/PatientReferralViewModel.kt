package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.ReferralUploadManager
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.http_sms_service.http.DatabaseObject
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import com.cradleplatform.neptune.http_sms_service.http.HttpSmsService
import com.cradleplatform.neptune.http_sms_service.http.Protocol
import com.cradleplatform.neptune.http_sms_service.sms.SMSSender
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.SmsReferral
import com.cradleplatform.neptune.utilities.AESEncryptor
import com.cradleplatform.neptune.utilities.CustomToast
import com.cradleplatform.neptune.utilities.SMSFormatter
import com.cradleplatform.neptune.utilities.jackson.JacksonMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PatientReferralViewModel @Inject constructor(
    private val httpSmsService: HttpSmsService,
    private val referralManager: ReferralManager, //for the internal database
    private val referralUploadManager: ReferralUploadManager, //for the backend database
    private val sharedPreferences: SharedPreferences,
    private val healthFacilityManager: HealthFacilityManager,
    @ApplicationContext private val app: Context
) : ViewModel() {

    val healthFacilityToUse = MediatorLiveData<String>()
    val comments = MutableLiveData("")

    private val selectedHealthFacilities: LiveData<List<HealthFacility>> =
        healthFacilityManager.getLiveListSelected

    /**
     * The health facilities that the user has selected in the settings.
     */
    val selectedHealthFacilitiesAsStrings: LiveData<Array<String>> =
        selectedHealthFacilities.map { it.map(HealthFacility::name).toTypedArray() }

    private val _areSendButtonsEnabled = MediatorLiveData<Boolean>()
    val areSendButtonsEnabled: LiveData<Boolean>
        get() = _areSendButtonsEnabled

    val isNetworkAvailable = NetworkAvailableLiveData(app)

    val isSending = MutableLiveData<Boolean>(false)

    init {
        healthFacilityToUse.apply {
            addSource(selectedHealthFacilitiesAsStrings) { userSelectedCentres ->
                val currentCentreToUse = healthFacilityToUse.value
                if (currentCentreToUse.isNullOrBlank()) return@addSource

                if (currentCentreToUse !in userSelectedCentres) {
                    // Clear out the selected health facility if the user removed the current from the
                    // user's selected health facilities.
                    healthFacilityToUse.value = ""
                }
            }
        }
        _areSendButtonsEnabled.apply {
            addSource(healthFacilityToUse) {
                // Only enabled if they have selected a health facility.
                val newEnabledState = !it.isNullOrBlank()
                if (value != newEnabledState) {
                    value = newEnabledState
                }
            }
        }
    }

    fun getActiveHealthFacility(): HealthFacility {
        val currentSelectedHealthFacilities = selectedHealthFacilities.value
        if (currentSelectedHealthFacilities.isNullOrEmpty()) error("missing health facilities")

        return currentSelectedHealthFacilities.find { it.name == healthFacilityToUse.value }
            ?: error("can't find")
    }

    @MainThread
    fun isSelectedHealthFacilityValid(): Boolean {
        val currentCentres = selectedHealthFacilitiesAsStrings.value ?: return false
        val healthFacilityToUse = healthFacilityToUse.value

        return isHealthCentreStringValid(healthFacilityToUse) && healthFacilityToUse in currentCentres
    }

    private fun isHealthCentreStringValid(healthFacility: String?) = !healthFacility.isNullOrBlank()

    suspend fun saveReferral(
        //referralOption: ReferralOption,
        submissionMode: String,
        patient: Patient,
        applicationContext: Context
    ): ReferralFlowSaveResult = withContext(Dispatchers.Default) {
        val currentTime = UnixTimestamp.now.toLong()
        //create a referral object
        val referral =
            Referral(
                id = UUID.randomUUID().toString(),
                comment = comments.value,
                referralHealthFacilityName = healthFacilityToUse.value
                    ?: error("No health facility selected"),
                dateReferred = currentTime,
                userId = sharedPreferences.getIntOrNull(LoginManager.USER_ID_KEY),
                patientId = patient.id,
                actionTaken = null,
                cancelReason = null,
                notAttendReason = null,
                isAssessed = false,
                isCancelled = false,
                notAttended = false,
                lastEdited = currentTime
            )

        /** we are redirected all transactions to the sms service **/
        var patientAndReferrals = PatientAndReferrals(patient, listOf(referral))
        val smsRelayRequestCounter = sharedPreferences.getLong(
            applicationContext.getString(R.string.sms_relay_request_counter), 0
        )

        val masterKeyAlias = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Create EncryptedSharedPreferences using the same master key
        val encryptedPrefs = EncryptedSharedPreferences.create(
            applicationContext,
            "encrypted_prefs",
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Retrieve the encrypted secret key from EncryptedSharedPreferences
        val smsSecretKey = encryptedPrefs.getString(LoginManager.SMS_K_Key, null)
            ?: // TODO: handle the case when the secret key is not available
            error("Encryption failed - no smsSecretKey is available")

        // This implementation is commented inside the PatientReferralActivity in the function sendSms(),
        // it has been moved since.
        val msgInPackets = SMSFormatter.listToString(
            SMSFormatter.formatSMS(
                SMSFormatter.encodeMsg(
                    JacksonMapper.createWriter<SmsReferral>().writeValueAsString(
                        SmsReferral(
                            patient = patientAndReferrals
                        )
                    ),
                    smsSecretKey
                ),
                smsRelayRequestCounter
            ),
        )
        // No point in saving to shared prefs perhaps? I do not know why they used it before, the message could be sent
        // as an argument
        sharedPreferences.edit(commit = true) {
            putString(applicationContext.getString(R.string.sms_relay_list_key), msgInPackets)
            putLong(
                applicationContext.getString(R.string.sms_relay_request_counter),
                smsRelayRequestCounter + 1
            )
        }

        /**
         * Sending an sms sender is also to the service is also not ideal
         * instead change the approach so that SMS sender becomes an injectable class
         * then inject and pass the sms content and let the class handle the sending inside a coroutine
         * this implementation exists in many places, changing all of them is not possible with the time available
         * so to not break the build an sms sender object is being sent to the service, REFER to issue
         */
        val smsSender = SMSSender(sharedPreferences, applicationContext)

        httpSmsService.upload(
            DatabaseObject.ReferralWrapper(
                patient,
                referral,
                smsSender,
                Protocol.valueOf(submissionMode)
            )
        )

        CustomToast.shortToast(
            applicationContext,
            applicationContext.getString(R.string.sms_sender_send)
        )

        // saves the data in internal db
        handleStoringReferralFromBuilders(referral)

        /**
         * This line is just a placeholder to avoid a return error.
         * Again, the way success is handled is not ideal, even if it might work
         * with the current structure, the flow is extremely disconnected and
         * does not work with all the components, we want a unified approach to
         * handling all sms / http transactions #refer to issue #111
         */
        return@withContext ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded

        /**
         * This is the previous implementation, now its upto the sms service to divide html and sms
         * ViewModels will only be responsible for requesting the service for upload
         */

        //Upload using the defined method ( Web or Sms)
        // when (referralOption) {
        //     ReferralOption.HTML -> {
        //         val result = referralUploadManager.uploadReferralViaWeb(patient, referral)
        //
        //         if (result is NetworkResult.Success) {
        //             // Store the referral object into internal DB
        //             handleStoringReferralFromBuilders(referral)
        //             return@withContext ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded
        //         }
        //         else {
        //             return@withContext ReferralFlowSaveResult.ErrorUploadingReferral
        //         }
        //     }
        //     ReferralOption.SMS -> {
        //         // Store the referral object into internal DB
        //         handleStoringReferralFromBuilders(referral)
        //
        //         // Pass a PatientAndReferrals object for the SMS message.
        //         return@withContext ReferralFlowSaveResult.SaveSuccessful.ReferralSmsNeeded(
        //             PatientAndReferrals(patient, listOf(referral))
        //         )
        //     }
        //     else -> error("unreachable")
        // }
    }

    /**
     * Will always save the referral to the database.
     */
    private suspend fun handleStoringReferralFromBuilders(
        referralFromBuilder: Referral
    ) {
        referralManager.addReferral(referralFromBuilder, false)
    }
}
