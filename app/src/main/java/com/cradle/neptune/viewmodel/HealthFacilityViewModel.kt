package com.cradle.neptune.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradle.neptune.manager.HealthFacilityManager
import com.cradle.neptune.model.HealthFacility
import kotlinx.coroutines.launch

/**
 * Useful since [AndroidViewModel] are lifecycle aware and can survive configuration changes.
 * manages data for access by UI in a safely manager
 */
class HealthFacilityViewModel @ViewModelInject constructor(
    private val healthFacilityManager: HealthFacilityManager
) : ViewModel() {

    fun getAllFacilities() = healthFacilityManager.getLiveList

    fun updateFacility(healthFacility: HealthFacility) {
        viewModelScope.launch { healthFacilityManager.update(healthFacility) }
    }
}
