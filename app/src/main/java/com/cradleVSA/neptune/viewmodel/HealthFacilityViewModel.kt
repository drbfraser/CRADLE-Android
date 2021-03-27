package com.cradleVSA.neptune.viewmodel

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleVSA.neptune.manager.HealthFacilityManager
import com.cradleVSA.neptune.model.HealthFacility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Useful since [AndroidViewModel] are lifecycle aware and can survive configuration changes.
 * manages data for access by UI in a safely manager
 */
@HiltViewModel
class HealthFacilityViewModel @Inject constructor(
    private val healthFacilityManager: HealthFacilityManager
) : ViewModel() {

    fun getAllFacilities() = healthFacilityManager.getLiveList

    suspend fun getAllSelectedFacilities() = healthFacilityManager.getAllSelectedByUser()

    fun updateFacility(healthFacility: HealthFacility) {
        viewModelScope.launch { healthFacilityManager.update(healthFacility) }
    }
}
