package com.cradle.neptune.manager.impl

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityEntity
import com.cradle.neptune.manager.HealthCentreManager

class HealthCentreManagerImpl(private val database: CradleDatabase) : HealthCentreManager {

    private val dao get() = database.healthFacilityDaoAccess()

    override suspend fun getById(id: String) = dao.getHealthFacilityById(id)

    override suspend fun getAll() = dao.allHealthFacilities

    override suspend fun getAllSelectedByUser() = dao.allUserSelectedHealthFacilities

    override suspend fun add(entity: HealthFacilityEntity) = dao.insert(entity)

    override suspend fun addAll(entities: List<HealthFacilityEntity>) = dao.insertAll(entities)

    override suspend fun update(entity: HealthFacilityEntity) {
        dao.update(entity)
    }

    override suspend fun removeById(id: String) {
        val entity = getById(id) ?: return
        dao.delete(entity)
    }

    override suspend fun deleteAllData() = dao.deleteAll()
}