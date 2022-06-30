package com.cradleplatform.neptune.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.ext.getIntOrNull
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.manager.LoginManager
import com.cradleplatform.neptune.manager.ReferralManager
import com.cradleplatform.neptune.manager.ReferralUploadManager
import com.cradleplatform.neptune.model.HealthFacility
import com.cradleplatform.neptune.model.Patient
import com.cradleplatform.neptune.model.PatientAndReferrals
import com.cradleplatform.neptune.model.Referral
import com.cradleplatform.neptune.net.NetworkResult
import com.cradleplatform.neptune.utilities.UnixTimestamp
import com.cradleplatform.neptune.utilities.livedata.NetworkAvailableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PatientReferralViewModel  @Inject constructor(
    private val referralManager: ReferralManager,
    private val referralUploadManager: ReferralUploadManager,
    private val sharedPreferences: SharedPreferences,
    private val healthFacilityManager: HealthFacilityManager,
    @ApplicationContext private val app: Context
) : ViewModel() {
    val healthFacilityToUse = MediatorLiveData<String>()
    val comments = MutableLiveData<String>("")

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
        referralOption: ReferralOption,
        patient: Patient
    ): ReferralFlowSaveResult = withContext(Dispatchers.Default) {
        val currentTime = UnixTimestamp.now.toLong()
        val referral =
            Referral(
                id = UUID.randomUUID().toString(),
                comment = comments.value,
                referralHealthFacilityName = healthFacilityToUse.value ?: error("No health facility selected"),
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

        when (referralOption) {
            ReferralOption.WEB -> {
                val result = referralUploadManager.uploadReferralViaWeb(patient, referral)

                if (result is NetworkResult.Success) {
                    // Save the patient and referral in local database
                    handleStoringReferralFromBuilders(referral)

                    return@withContext ReferralFlowSaveResult.SaveSuccessful.NoSmsNeeded
                } else {
                    return@withContext ReferralFlowSaveResult.ErrorUploadingReferral
                }
            }
            ReferralOption.SMS -> {
                // Store the referral object into internal DB
                handleStoringReferralFromBuilders(referral)

                // Pass a PatientAndReferrals object for the SMS message.
                return@withContext ReferralFlowSaveResult.SaveSuccessful.ReferralSmsNeeded(
                    PatientAndReferrals(patient, listOf(referral))
                )
            }
            else -> error("unreachable")
        }
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