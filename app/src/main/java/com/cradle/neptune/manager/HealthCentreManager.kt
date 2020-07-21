package com.cradle.neptune.manager

import com.cradle.neptune.database.CradleDatabase
import com.cradle.neptune.database.HealthFacilityEntity

class HealthCentreManager(private val database: CradleDatabase)  {

    private val dao get() = database.healthFacilityDaoAccess()

      fun getById(id: String) = dao.getHealthFacilityById(id)

      fun getAll() = dao.allHealthFacilities

      fun getAllSelectedByUser() = dao.allUserSelectedHealthFacilities

      fun add(entity: HealthFacilityEntity) = dao.insert(entity)

      fun addAll(entities: List<HealthFacilityEntity>) = dao.insertAll(entities)

      fun update(entity: HealthFacilityEntity) {
        dao.update(entity)
    }

      fun removeById(id: String) {
        val entity = getById(id) ?: return
        dao.delete(entity)
    }

      fun deleteAllData() = dao.deleteAll()
}
