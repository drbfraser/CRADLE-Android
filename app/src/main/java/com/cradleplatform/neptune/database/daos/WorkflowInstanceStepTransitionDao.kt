package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cradleplatform.neptune.model.WorkflowInstanceStepTransition

/**
 * Data Access Object (DAO) for [WorkflowInstanceStepTransition] entities.
 */
@Dao
interface WorkflowInstanceStepTransitionDao {
    @Insert
    suspend fun insert(transition: WorkflowInstanceStepTransition)

    @Query("SELECT * FROM WorkflowInstanceStepTransition ORDER BY id ASC")
    suspend fun getAll(): List<WorkflowInstanceStepTransition>

    @Query("DELETE FROM WorkflowInstanceStepTransition WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM WorkflowInstanceStepTransition WHERE instanceId = :instanceId")
    suspend fun deleteByInstanceId(instanceId: String)
}
