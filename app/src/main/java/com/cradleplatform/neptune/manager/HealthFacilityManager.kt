package com.cradleplatform.neptune.manager

import androidx.lifecycle.LiveData
import com.cradleplatform.neptune.database.CradleDatabase
import com.cradleplatform.neptune.model.HealthFacility

/**
 *  * manager to interact with the [HealthFacility] table
 *Added [suspend] function so that there is compile time error when inserting on DB through
 * main thread rather than run time error
 */
@Suppress("RedundantSuspendModifier")
class HealthFacilityManager(database: CradleDatabase) {

    private val dao = database.healthFacility()

    /**
     * get all the [HealthFacility] selected by the current user.
     */
    suspend fun getAllSelectedByUser() = dao.getAllUserSelectedHealthFacilities()

    /**
     * get all the [HealthFacility] currently in database
     */
    suspend fun getAllFacilities() = dao.getAllHealthFacilities()

    /**
     * add a single health facility
     */
    suspend fun add(facility: HealthFacility) = dao.insert(facility)

    /**
     * Add all the health facilities
     */
    suspend fun addAll(facilities: List<HealthFacility>) = dao.insertAll(facilities)

    /**
     * update a single Health Facility
     */
    suspend fun update(facility: HealthFacility) = dao.update(facility)

    /**
     * returns a live list of the facilities
     */
    val getLiveList: LiveData<List<HealthFacility>> = dao.getAllFacilitiesLiveData()

    /**
     * Returns a list of all user-selected health facilities as LiveData.
     */
    val getLiveListSelected: LiveData<List<HealthFacility>> =
        dao.getAllUserSelectedHealthFacilitiesLiveData()
}
