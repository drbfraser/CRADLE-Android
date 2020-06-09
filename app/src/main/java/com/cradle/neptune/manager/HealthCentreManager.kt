package com.cradle.neptune.manager

import com.cradle.neptune.database.HealthFacilityEntity
import kotlinx.coroutines.*

/**
 * Service for interfacing with health centres stored in the database.
 */
interface HealthCentreManager {

    /**
     * Returns the health centre with a given [id] if it exists.
     *
     * @param id the id to search for
     */
    suspend fun getById(id: String): HealthFacilityEntity?

    /**
     * Returns all heath centres stored in the database.
     */
    suspend fun getAll(): List<HealthFacilityEntity>

    /**
     * Returns all heath centres in the database which the user has selected.
     */
    suspend fun getAllSelectedByUser(): List<HealthFacilityEntity>

    /**
     * Adds a new health centre to the database.
     *
     * @param entity the health centre to add
     */
    suspend fun add(entity: HealthFacilityEntity)

    /**
     * Adds a list of health centres to the database.
     *
     * @param entities the list of health centres to add
     */
    suspend fun addAll(entities: List<HealthFacilityEntity>)

    /**
     * Updates an existing heath centre.
     *
     * @param entity the entity to update
     */
    suspend fun update(entity: HealthFacilityEntity)

    /**
     * Removes the health centre with a given [id].
     *
     * If no such health centre exists, then this method should do nothing.
     *
     * @param id the id of the health centre to remove
     */
    suspend fun removeById(id: String)

    /**
     * Async variant of [getById].
     */
    fun getByIdAsync(id: String) = GlobalScope.async(Dispatchers.IO) { getById(id) }

    /**
     * Blocking variant of [getById].
     */
    fun getByIdBlocking(id: String) = runBlocking { getById(id) }

    /**
     * Async variant of [getAll].
     */
    fun getAllAsync() = GlobalScope.async(Dispatchers.IO) { getAll() }

    /**
     * Blocking variant of [getAll].
     */
    fun getAllBlocking() = runBlocking { getAll() }

    /**
     * Async variant of [getAllSelectedByUser].
     */
    fun getAllSelectedByUserAsync() = GlobalScope.async(Dispatchers.IO) { getAllSelectedByUser() }

    /**
     * Blocking variant of [getAllSelectedByUser].
     */
    fun getAllSelectedByUserBlocking() = runBlocking { getAllSelectedByUser() }

    /**
     * Async variant of [add].
     */
    fun addAsync(entity: HealthFacilityEntity) = GlobalScope.async(Dispatchers.IO) { add(entity) }

    /**
     * Async variant of [addAll].
     */
    fun addAllAsync(entities: List<HealthFacilityEntity>) = GlobalScope.async(Dispatchers.IO) { addAll(entities) }

    /**
     * Async variant of [update].
     */
    fun updateAsync(entity: HealthFacilityEntity) = GlobalScope.async(Dispatchers.IO) { update(entity) }

    /**
     * Async variant of [removeById].
     */
    fun removeByIdAsync(id: String) = GlobalScope.async(Dispatchers.IO) { removeById(id) }
}