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

    @Query("SELECT * FROM WorkflowInstance WHERE id = :id")
    suspend fun getById(id: String): WorkflowInstance?

    @Query("SELECT * FROM WorkflowInstance WHERE isUploadedToServer = 0")
    suspend fun getUnuploaded(): List<WorkflowInstance>

    @Query("DELETE FROM WorkflowInstance WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM WorkflowInstance WHERE isUploadedToServer = 1")
    suspend fun deleteAllUploaded()

    @Query("DELETE FROM WorkflowInstance")
    suspend fun deleteAll()
}
