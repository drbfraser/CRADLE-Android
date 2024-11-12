package com.cradleplatform.neptune.viewmodel.patients

import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.http_sms_service.http.NetworkResult
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.viewmodel.UserViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PatientReferralViewModel @Inject constructor(
    private val referralManager: ReferralManager, //for the internal database
    networkStateManager: NetworkStateManager,
    private val sharedPreferences: SharedPreferences,
    healthFacilityManager: HealthFacilityManager,
    private val restApi: RestApi
) : ViewModel() {

    @Inject
    lateinit var smsDataProcessor: SMSDataProcessor
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
    val isNetworkAvailable: LiveData<Boolean> =
        networkStateManager.getInternetConnectivityStatus()
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
        protocol: Protocol,
        patient: Patient,
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
                userId = sharedPreferences.getIntOrNull(UserViewModel.USER_ID_KEY),
                patientId = patient.id,
                actionTaken = null,
                cancelReason = null,
                notAttendReason = null,
                isAssessed = false,
                isCancelled = false,
                notAttended = false,
                lastEdited = currentTime
            )

        val patientExists: Boolean =
            when (val result = restApi.getPatientInfo(patient.id, protocol)) {
                is NetworkResult.Success -> true
                is NetworkResult.Failure -> if (result.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    false
                } else {
                    return@withContext ReferralFlowSaveResult.ErrorUploadingReferral
                }
                is NetworkResult.NetworkException -> return@withContext ReferralFlowSaveResult.ErrorUploadingReferral
            }

        if (patientExists) {
            restApi.postReferral(referral, protocol)
        } else {
            restApi.postPatient(PatientAndReferrals(patient, listOf(referral)), protocol)
        }

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
