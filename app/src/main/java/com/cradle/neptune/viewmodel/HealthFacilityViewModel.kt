package com.cradle.neptune.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.HealthCentreManager
import com.cradle.neptune.model.HealthFacility
import javax.inject.Inject

/**
 * Useful since [AndroidViewModel] are lifecycle aware and can survive configuration changes.
 * manages data for access by UI in a safely manager
 */
class HealthFacilityViewModel(application: Application) : AndroidViewModel(application) {
    @Inject
    lateinit var healthCentreManager: HealthCentreManager

    init {
        (getApplication() as MyApp).appComponent.inject(this)
    }

    fun getAllFacilities() = healthCentreManager.getLiveList

    fun updateFacility(healthfacility: HealthFacility) = healthCentreManager.update(healthfacility)
}
