package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cradleplatform.neptune.model.WorkflowInstance

/**
 * Data Access Object (DAO) for [WorkflowInstance] entities.
 */
@Dao
interface WorkflowInstanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workflowInstances: List<WorkflowInstance>)

    @Query("SELECT * FROM WorkflowInstance WHERE patientId = :patientId")
    suspend fun getByPatientId(patientId: String): List<WorkflowInstance>

    @Query("DELETE FROM WorkflowInstance")
    suspend fun deleteAll()
}
