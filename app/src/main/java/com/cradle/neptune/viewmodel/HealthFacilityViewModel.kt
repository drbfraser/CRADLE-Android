package com.cradle.neptune.viewmodel

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.model.HealthFacility

/**
 * Useful since [AndroidViewModel] are lifecycle aware and can survive configuration changes.
 * manages data for access by UI in a safely manager
 */
class HealthFacilityViewModel @ViewModelInject constructor(
    private val healthCentreManager: HealthCentreManager
) : ViewModel() {

    fun getAllFacilities() = healthCentreManager.getLiveList

    fun updateFacility(healthFacility: HealthFacility) = healthCentreManager.update(healthFacility)
}
