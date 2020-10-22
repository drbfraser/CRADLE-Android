package com.cradle.neptune.viewmodel

import androidx.annotation.MainThread
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.model.HealthFacility

class ReferralDialogViewModel @ViewModelInject constructor(
    healthCentreManager: HealthCentreManager
) : ViewModel() {
    val healthCentreToUse = MediatorLiveData<String>()
    val comments = MutableLiveData<String>("")

    private val selectedHealthCentres: LiveData<List<HealthFacility>> =
        healthCentreManager.getLiveListSelected

    /**
     * The health centres that the user has selected in the settings.
     */
    val selectedHealthCentresAsStrings: LiveData<Array<String>> =
        selectedHealthCentres.map { it.map(HealthFacility::name).toTypedArray() }

    private val _areSendButtonsEnabled = MediatorLiveData<Boolean>()
    val areSendButtonsEnabled: LiveData<Boolean>
        get() = _areSendButtonsEnabled

    val isSending = MutableLiveData<Boolean>(false)

    init {
        healthCentreToUse.apply {
            addSource(selectedHealthCentresAsStrings) { userSelectedCentres ->
                val currentCentreToUse = healthCentreToUse.value
                if (currentCentreToUse.isNullOrBlank()) return@addSource

                if (currentCentreToUse !in userSelectedCentres) {
                    // Clear out the selected health centre if the user removed the current from the
                    // user's selected health centres.
                    healthCentreToUse.value = ""
                }
            }
        }
        _areSendButtonsEnabled.apply {
            addSource(healthCentreToUse) {
                // Only enabled if they have selected a health centre.
                val newEnabledState = !it.isNullOrBlank()
                if (value != newEnabledState) {
                    value = newEnabledState
                }
            }
        }
    }

    fun getHealthCentreFromHealthCentreName(name: String): HealthFacility {
        val currentSelectedHealthCentres = selectedHealthCentres.value
        if (currentSelectedHealthCentres.isNullOrEmpty()) error("missing health centres")

        return currentSelectedHealthCentres.find { it.name == name }
            ?: error("can't find")
    }

    @MainThread
    fun isSelectedHealthCentreValid(): Boolean {
        val currentCentres = selectedHealthCentresAsStrings.value ?: return false
        val healthCentreToUse = healthCentreToUse.value

        return isHealthCentreStringValid(healthCentreToUse) && healthCentreToUse in currentCentres
    }

    private fun isHealthCentreStringValid(healthCentre: String?) = !healthCentre.isNullOrBlank()
}
