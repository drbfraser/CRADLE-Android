package com.cradleVSA.neptune.manager

import androidx.lifecycle.LiveData
import com.cradleVSA.neptune.database.CradleDatabase
import com.cradleVSA.neptune.model.HealthFacility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  * manager to interact with the [HealthFacility] table
 *Added [suspend] function so that there is compile time error when inserting on DB through
 * main thread rather than run time error
 */
@Suppress("RedundantSuspendModifier")
class HealthFacilityManager(database: CradleDatabase) {

    private val dao = database.healthFacility()

    /**
     * get a [HealthFacility] by id
     */
    suspend fun getById(id: String) = withContext(Dispatchers.IO) { dao.getHealthFacilityById(id) }

    /**
     * get all the [HealthFacility] selected by the current user.
     */
    suspend fun getAllSelectedByUser() = withContext(Dispatchers.IO) {
        dao.getAllUserSelectedHealthFacilities()
    }

    /**
     * add a single health facility
     */
    suspend fun add(facility: HealthFacility) = withContext(Dispatchers.IO) { dao.insert(facility) }

    /**
     * Add all the health facilities
     */
    suspend fun addAll(facilities: List<HealthFacility>) = withContext(Dispatchers.IO) {
        dao.insertAll(facilities)
    }

    /**
     * update a single Health Facility
     */
    suspend fun update(facility: HealthFacility) = withContext(Dispatchers.IO) { dao.update(facility) }

    /**
     * returns a live list of the facilities
     */
    val getLiveList: LiveData<List<HealthFacility>> = dao.getAllFacilitiesLiveData()

    /**
     * Returns a list of all user-selected health facilities as LiveData.
     */
    val getLiveListSelected: LiveData<List<HealthFacility>> =
        dao.getAllUserSelectedHealthFacilitiesLiveData()

    /**
     * delete all [HealthFacility] from the DB
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) { dao.deleteAll() }
}
