package com.cradleplatform.neptune.viewmodel.patients

import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.http_sms_service.http.RestApi
import com.cradleplatform.neptune.http_sms_service.sms.utils.SMSDataProcessor
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.PatientManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.viewmodel.UserViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PatientReferralViewModel @Inject constructor(
    private val referralManager: ReferralManager, //for the internal database
    private val patientManager: PatientManager,
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

    /**
     * Create a [Referral] object.
     */
    fun buildReferral(patient: Patient): Referral {
        val currentTime = UnixTimestamp.now.toLong()
        return Referral(
            id = UUID.randomUUID().toString(),
            comment = comments.value,
            healthFacilityName = healthFacilityToUse.value
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
    }

    override fun onCleared() {
        Log.i("PatientReferralViewModel", "onCleared()")
        super.onCleared()
    }
}
