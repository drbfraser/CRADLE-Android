package com.cradleplatform.neptune.viewmodel.settings

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cradleplatform.neptune.manager.HealthFacilityManager
import com.cradleplatform.neptune.model.HealthFacility
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

    fun updateFacility(healthFacility: HealthFacility) {
        viewModelScope.launch { healthFacilityManager.update(healthFacility) }
    }
}
