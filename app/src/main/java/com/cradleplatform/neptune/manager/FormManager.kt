package com.cradleplatform.neptune.manager

import androidx.lifecycle.LiveData
import com.cradleplatform.neptune.database.daos.FormClassificationDao
import com.cradleplatform.neptune.model.FormClassification
import com.cradleplatform.neptune.model.FormTemplate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for FormTemplate and FormClassifications
 *
 * Interacts with the [FormClassification] table in the database.
 */
@Singleton
class FormManager @Inject constructor (
    private val formClassDao: FormClassificationDao
) {

    suspend fun searchForFormTemplateWithName(formClassName: String): List<FormTemplate> =
        formClassDao.getFormTemplateByName(formClassName)

    suspend fun addFormByClassification(formClass: FormClassification) =
        formClassDao.addOrUpdateFormClassification(formClass)

    fun getLiveDataFormTemplates(): LiveData<List<FormTemplate>> =
        formClassDao.getAllFormTemplates()

    fun getLiveDataFormClassifications(): LiveData<List<FormClassification>> =
        formClassDao.getAllFormClassifications()
}
