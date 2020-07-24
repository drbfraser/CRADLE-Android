package com.cradle.neptune.manager

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.model.HealthFacility
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HealthCentreManager(private val database: CradleDatabase) {

    private val dao get() = database.healthFacilityDaoAccess()

    /**
     * get a [HealthFacility] by id
     */
    suspend fun getById(id: String) = dao.getHealthFacilityById(id)

    /**
     * get all the health facilities
     */
    suspend fun getAll() = dao.allHealthFacilities

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllBlocking() = runBlocking { getAll() }

    /**
     * get all the [HealthFacility] selected by the current user.
     */
    suspend fun getAllSelectedByUser() = dao.allUserSelectedHealthFacilities

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllSelectedByUserBlocking() = runBlocking { getAllSelectedByUser() }

    /**
     * add a single health facility
     */
    suspend fun add(entity: HealthFacility) = dao.insert(entity)

    /**
     * Add all the health facilities
     */
    suspend fun addAll(entities: List<HealthFacility>) = dao.insertAll(entities)

    /**
     * update a single Health Facility
     */
    fun update(entity: HealthFacility) = GlobalScope.launch { dao.update(entity) }

    /**
     * delete a health facility by id
     */
    suspend fun deleteById(id: String) {
    val entity = getById(id) ?: return
    dao.delete(entity)
    }

    /**
     * delete all [HealthFacility] from the DB
     */
    suspend fun deleteAll() = dao.deleteAll()
}
