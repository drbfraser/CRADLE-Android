package com.cradleplatform.neptune.viewmodel.newPatient

import androidx.annotation.MainThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.model.HealthFacility
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ReferralDialogViewModel @Inject constructor(
    healthFacilityManager: HealthFacilityManager
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

    fun getHealthFacilityFromHealthFacilityName(name: String): HealthFacility {
        val currentSelectedHealthFacilities = selectedHealthFacilities.value
        if (currentSelectedHealthFacilities.isNullOrEmpty()) error("missing health facilities")

        return currentSelectedHealthFacilities.find { it.name == name }
            ?: error("can't find")
    }

    @MainThread
    fun isSelectedHealthFacilityValid(): Boolean {
        val currentCentres = selectedHealthFacilitiesAsStrings.value ?: return false
        val healthFacilityToUse = healthFacilityToUse.value

        return isHealthCentreStringValid(healthFacilityToUse) && healthFacilityToUse in currentCentres
    }

    private fun isHealthCentreStringValid(healthFacility: String?) = !healthFacility.isNullOrBlank()
}
