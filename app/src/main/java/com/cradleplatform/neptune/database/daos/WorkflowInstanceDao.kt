package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cradleplatform.neptune.model.WorkflowInstance

@Dao
interface WorkflowInstanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workflowInstance: WorkflowInstance)

    @Query("SELECT * FROM WorkflowInstance WHERE id = :id")
    suspend fun getById(id: String): WorkflowInstance?

    @Query("SELECT * FROM WorkflowInstance WHERE patientId = :patientId")
    suspend fun getByPatientId(patientId: String): List<WorkflowInstance>

    @Query("SELECT * FROM WorkflowInstance WHERE isUploadedToServer = 0")
    suspend fun getWorkflowInstancesToUpload(): List<WorkflowInstance>

    @Query("SELECT COUNT(id) FROM WorkflowInstance WHERE isUploadedToServer = 0")
    suspend fun countWorkflowInstancesToUpload(): Int

    @Query("DELETE FROM WorkflowInstance")
    suspend fun deleteAll()
}
