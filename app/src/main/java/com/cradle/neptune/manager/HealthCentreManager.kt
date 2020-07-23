package com.cradle.neptune.manager

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityEntity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HealthCentreManager(private val database: CradleDatabase) {

    private val dao get() = database.healthFacilityDaoAccess()

    suspend fun getById(id: String) = dao.getHealthFacilityById(id)

    suspend fun getAll() = dao.allHealthFacilities

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllBlocking() = runBlocking { getAll() }

    suspend fun getAllSelectedByUser() = dao.allUserSelectedHealthFacilities

    /**
     * TODO: once all the java classes calling this method are turned into Kotlin,
     * remove this function and call the corressponding method.
     * This is only for legacy java code still calling this function.
     */
    @Deprecated("Please avoid using this function in Kotlin files.")
    fun getAllSelectedByUserBlocking() = runBlocking { getAllSelectedByUser() }

    suspend fun add(entity: HealthFacilityEntity) = dao.insert(entity)

    suspend fun addAll(entities: List<HealthFacilityEntity>) = dao.insertAll(entities)

    fun update(entity: HealthFacilityEntity) = GlobalScope.launch { dao.update(entity) }

    suspend fun removeById(id: String) {
    val entity = getById(id) ?: return
    dao.delete(entity)
    }

    suspend fun deleteAllData() = dao.deleteAll()
}
