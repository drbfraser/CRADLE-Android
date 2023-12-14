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
     * All [FormClassification]s in the table as LiveData List
     */
    @Query("SELECT * FROM FormClassification ORDER BY formClassName ASC")
    fun getAllFormClassifications(): LiveData<List<FormClassification>>

    /**
     * All [FormTemplate]s in the table as LiveData List
     */
    @Query("SELECT formTemplate FROM FormClassification ORDER BY formClassName ASC")
    fun getAllFormTemplates(): LiveData<List<FormTemplate>>

    /**
     * Search for a list of [FormTemplate]s with their name(Classification) = [name]
     *
     * @param name the name of classification to search for
     */
    @Query("SELECT formTemplate FROM FormClassification WHERE formClassName = :name")
    suspend fun getFormTemplateByName(name: String): List<FormTemplate>

    /**
     * Search for a [formClassName] by id(Classification) = [id]
     *
     * @param id the id of classification to search for
     */
    @Query("SELECT formClassName FROM FormClassification WHERE formClassId = :formClassId")
    suspend fun getFormClassNameById(formClassId: String): String

    /**
     * Search for a [FormTemplate] by id(Classification) = [id]
     *
     * @param id the id of classification to search for
     */
    @Query("SELECT formTemplate FROM FormClassification WHERE formClassId = :formClassId")
    suspend fun getFormTemplateById(formClassId: String): FormTemplate
}
