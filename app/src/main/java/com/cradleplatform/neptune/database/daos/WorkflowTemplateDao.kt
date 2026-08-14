package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cradleplatform.neptune.model.WorkflowTemplate

@Dao
interface WorkflowTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workflowTemplate: WorkflowTemplate)

    @Query("SELECT * FROM WorkflowTemplate")
    suspend fun getAll(): List<WorkflowTemplate>

    @Query("SELECT * FROM WorkflowTemplate WHERE id = :id")
    suspend fun getById(id: String): WorkflowTemplate?

    @Query("DELETE FROM WorkflowTemplate")
    suspend fun deleteAll()
}
