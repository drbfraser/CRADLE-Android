package com.cradle.neptune.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.model.HealthFacility
import kotlinx.coroutines.launch

/**
 * Useful since [AndroidViewModel] are lifecycle aware and can survive configuration changes.
 * manages data for access by UI in a safely manager
 */
class HealthFacilityViewModel @ViewModelInject constructor(
    private val healthCentreManager: HealthCentreManager
) : ViewModel() {

    fun getAllFacilities() = healthCentreManager.getLiveList

    fun updateFacility(healthFacility: HealthFacility) {
        viewModelScope.launch { healthCentreManager.update(healthFacility) }
    }
}
