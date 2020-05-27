package com.cradle.neptune.service.impl

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityEntity
import com.cradle.neptune.service.HealthCentreService

class HealthCentreServiceImpl(private val database: CradleDatabase) : HealthCentreService {

    private val dao get() = database.healthFacilityDaoAccess()

    override suspend fun getById(id: String) = dao.getHealthFacilityById(id)

    override suspend fun getAll() = dao.allHealthFacilities

    override suspend fun getAllSelectedByUser() = dao.allUserSelectedHealthFacilities

    override suspend fun add(entity: HealthFacilityEntity) {
        dao.insert(entity)
    }

    override suspend fun update(entity: HealthFacilityEntity) {
        dao.update(entity)
    }

    override suspend fun removeById(id: String) {
        val entity = getById(id) ?: return
        dao.delete(entity)
    }
}