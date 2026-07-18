package com.cradleplatform.neptune.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cradleplatform.neptune.model.WorkflowTemplate

/**
 * Data Access Object (DAO) for [WorkflowTemplate] entities.
 */
@Dao
interface WorkflowTemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workflowTemplates: List<WorkflowTemplate>)

    @Query("SELECT * FROM WorkflowTemplate")
    suspend fun getAll(): List<WorkflowTemplate>

    @Query("DELETE FROM WorkflowTemplate")
    suspend fun deleteAll()
}
