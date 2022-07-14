package com.cradleplatform.neptune.database.daos

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormTemplate

@Dao
interface FormClassificationDao {

    /**
     * Inserts a new FormClassification or update current FormClassification if (formClass + language) is same
     *
     * @param formClass an Entity of FormClassification to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addOrUpdateFormClassification(formClass: FormClassification)

    /**
     * All FormClassifications in the table as LiveData List
     */
    @Query("SELECT * FROM FormClassification ORDER BY formClass ASC")
    fun getFormClassifications(): LiveData<List<FormClassification>>

    /**
     * All FormTemplates as LiveData List
     */
    @Query("SELECT formTemplate FROM FormClassification ORDER BY formClass ASC")
    fun getFormTemplates(): LiveData<List<FormTemplate>>
}
